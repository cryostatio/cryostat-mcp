/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.mcp.k8s;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CryostatInstanceDiscovery {

    private static final Logger LOG = Logger.getLogger(CryostatInstanceDiscovery.class);
    private static final String LABEL_PART_OF = "app.kubernetes.io/part-of";
    private static final String LABEL_COMPONENT = "app.kubernetes.io/component";
    private static final String CRYOSTAT_PART_OF = "cryostat";
    private static final String CRYOSTAT_COMPONENT = "cryostat";

    private final ExecutorService discoveryExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, CryostatInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> namespaceToInstances = new ConcurrentHashMap<>();
    private Watch watch;

    @Inject KubernetesClient k8sClient;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Starting Cryostat instance discovery");
        discoveryExecutor.submit(
                () -> {
                    try {
                        LOG.info("Started Cryostat instance discovery");
                        discoverInstances();
                        startWatch();
                        LOG.info("Cryostat instance discovery initialized");
                    } catch (Exception e) {
                        LOG.error("Failed to initialize Cryostat instance discovery", e);
                    }
                });
    }

    void onShutdown(@Observes ShutdownEvent event) {
        LOG.info("Shutting down Cryostat instance discovery");
        if (watch != null) {
            watch.close();
        }
        discoveryExecutor.shutdown();
    }

    private void discoverInstances() {
        try {
            List<Service> services =
                    k8sClient
                            .services()
                            .inAnyNamespace()
                            .withLabel(LABEL_PART_OF, CRYOSTAT_PART_OF)
                            .withLabel(LABEL_COMPONENT, CRYOSTAT_COMPONENT)
                            .list()
                            .getItems();

            for (Service svc : services) {
                handleServiceAdded(svc);
            }

            LOG.infof("Discovered %d Cryostat instances", instances.size());
        } catch (Exception e) {
            LOG.error("Failed to discover Cryostat instances", e);
        }
    }

    private void startWatch() {
        try {
            watch =
                    k8sClient
                            .services()
                            .inAnyNamespace()
                            .withLabel(LABEL_PART_OF, CRYOSTAT_PART_OF)
                            .withLabel(LABEL_COMPONENT, CRYOSTAT_COMPONENT)
                            .watch(
                                    new Watcher<Service>() {
                                        @Override
                                        public void eventReceived(Action action, Service svc) {
                                            handleWatchEvent(action, svc);
                                        }

                                        @Override
                                        public void onClose(WatcherException e) {
                                            if (e != null) {
                                                LOG.error("Watch closed with error, restarting", e);
                                                startWatch();
                                            } else {
                                                LOG.info("Watch closed normally");
                                            }
                                        }
                                    });
            LOG.info("Started watching Cryostat Services");
        } catch (Exception e) {
            LOG.error("Failed to start watch", e);
        }
    }

    private void handleWatchEvent(Watcher.Action action, Service svc) {
        String namespace = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();

        switch (action) {
            case ADDED:
                LOG.infof("Cryostat Service added: %s/%s", namespace, name);
                handleServiceAdded(svc);
                break;

            case MODIFIED:
                LOG.infof("Cryostat Service modified: %s/%s", namespace, name);
                handleServiceModified(svc);
                break;

            case DELETED:
                LOG.infof("Cryostat Service deleted: %s/%s", namespace, name);
                handleServiceDeleted(svc);
                break;

            case ERROR:
                LOG.errorf("Watch error for Service: %s/%s", namespace, name);
                break;
        }
    }

    private void handleServiceAdded(Service svc) {
        CryostatInstance instance = toCryostatInstance(svc);
        if (instance != null) {
            String key = instanceKey(instance);
            instances.put(key, instance);
            updateNamespaceMapping(instance, null);
        }
    }

    private void handleServiceModified(Service svc) {
        CryostatInstance newInstance = toCryostatInstance(svc);
        if (newInstance != null) {
            String key = instanceKey(newInstance);
            CryostatInstance oldInstance = instances.get(key);
            instances.put(key, newInstance);
            updateNamespaceMapping(newInstance, oldInstance);
        }
    }

    private void handleServiceDeleted(Service svc) {
        String namespace = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();
        String key = namespace + "/" + name;
        CryostatInstance instance = instances.remove(key);
        if (instance != null) {
            updateNamespaceMapping(null, instance);
        }
    }

    private void updateNamespaceMapping(
            CryostatInstance newInstance, CryostatInstance oldInstance) {
        if (oldInstance != null) {
            for (String ns : oldInstance.targetNamespaces()) {
                Set<String> instanceNames = namespaceToInstances.get(ns);
                if (instanceNames != null) {
                    instanceNames.remove(oldInstance.name());
                    if (instanceNames.isEmpty()) {
                        namespaceToInstances.remove(ns);
                    }
                }
            }
        }

        if (newInstance != null) {
            for (String ns : newInstance.targetNamespaces()) {
                namespaceToInstances
                        .computeIfAbsent(ns, k -> ConcurrentHashMap.newKeySet())
                        .add(newInstance.name());
            }
        }
    }

    private CryostatInstance toCryostatInstance(Service svc) {
        String namespace = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();

        // Verify the service has the expected labels
        Map<String, String> labels = svc.getMetadata().getLabels();
        if (labels == null
                || !CRYOSTAT_PART_OF.equals(labels.get(LABEL_PART_OF))
                || !CRYOSTAT_COMPONENT.equals(labels.get(LABEL_COMPONENT))) {
            LOG.warnf(
                    "Service %s/%s does not have expected Cryostat labels, skipping",
                    namespace, name);
            return null;
        }

        // Determine target namespaces
        // For Helm-deployed instances, we need to check the Cryostat configuration
        // For now, default to monitoring the same namespace as the service
        Set<String> targetNamespaces = new HashSet<>();
        targetNamespaces.add(namespace);

        // Determine the application URL
        String applicationUrl = determineApplicationUrl(svc);

        return new CryostatInstance(name, namespace, applicationUrl, targetNamespaces);
    }

    private String determineApplicationUrl(Service svc) {
        String namespace = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();

        ServicePort port = findServicePort(svc);
        if (port != null) {
            boolean tls = port.getAppProtocol() != null && port.getAppProtocol().equals("https");
            return String.format(
                    "http%s://%s.%s.svc:%d", tls ? "s" : "", name, namespace, port.getPort());
        }

        // Fallback to default HTTPS port
        return String.format("https://%s.%s.svc:8181", name, namespace);
    }

    private ServicePort findServicePort(Service svc) {
        List<ServicePort> ports = svc.getSpec().getPorts();

        // First, try to find by appProtocol, preferring https over http
        for (ServicePort port : ports) {
            if ("https".equals(port.getAppProtocol())) {
                return port;
            }
        }

        for (ServicePort port : ports) {
            if ("http".equals(port.getAppProtocol())) {
                return port;
            }
        }

        // If no appProtocol match, try to find by port name
        for (ServicePort port : ports) {
            if (port.getName() != null && port.getName().endsWith("https")) {
                return port;
            }
        }

        for (ServicePort port : ports) {
            if (port.getName() != null && port.getName().endsWith("http")) {
                return port;
            }
        }

        // Return the first port as a last resort
        return ports.isEmpty() ? null : ports.get(0);
    }

    public Optional<CryostatInstance> findByNamespace(String namespace) {
        Set<String> instanceNames = namespaceToInstances.get(namespace);
        if (instanceNames == null || instanceNames.isEmpty()) {
            return Optional.empty();
        }

        List<CryostatInstance> matches = new ArrayList<>();
        for (String instanceName : instanceNames) {
            for (CryostatInstance instance : instances.values()) {
                if (instance.name().equals(instanceName)) {
                    matches.add(instance);
                    break;
                }
            }
        }

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        Collections.sort(matches);

        if (matches.size() > 1) {
            LOG.warnf(
                    "Multiple Cryostat instances monitor namespace '%s': %s. Selected: %s"
                            + " (deterministic tiebreaker)",
                    namespace,
                    matches.stream().map(CryostatInstance::name).toList(),
                    matches.get(0).name());
        }

        return Optional.of(matches.get(0));
    }

    public List<CryostatInstance> findAllByNamespace(String namespace) {
        Set<String> instanceNames = namespaceToInstances.get(namespace);
        if (instanceNames == null || instanceNames.isEmpty()) {
            return List.of();
        }

        List<CryostatInstance> matches = new ArrayList<>();
        for (String instanceName : instanceNames) {
            for (CryostatInstance instance : instances.values()) {
                if (instance.name().equals(instanceName)) {
                    matches.add(instance);
                    break;
                }
            }
        }

        Collections.sort(matches);
        return matches;
    }

    public Collection<CryostatInstance> getAllInstances() {
        return new ArrayList<>(instances.values());
    }

    public Map<String, List<CryostatInstance>> getNamespaceMapping() {
        Map<String, List<CryostatInstance>> mapping = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : namespaceToInstances.entrySet()) {
            String namespace = entry.getKey();
            List<CryostatInstance> instanceList = new ArrayList<>();

            for (String instanceName : entry.getValue()) {
                for (CryostatInstance instance : instances.values()) {
                    if (instance.name().equals(instanceName)) {
                        instanceList.add(instance);
                        break;
                    }
                }
            }

            Collections.sort(instanceList);
            mapping.put(namespace, instanceList);
        }

        return mapping;
    }

    private String instanceKey(CryostatInstance instance) {
        return instance.namespace() + "/" + instance.name();
    }
}

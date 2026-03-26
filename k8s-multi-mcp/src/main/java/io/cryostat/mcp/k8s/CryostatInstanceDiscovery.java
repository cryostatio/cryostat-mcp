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

import io.cryostat.mcp.k8s.model.Cryostat;

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

    @Inject KubernetesClient k8sClient;

    private final Map<String, CryostatInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> namespaceToInstances = new ConcurrentHashMap<>();
    private Watch watch;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Starting Cryostat instance discovery");
        discoverInstances();
        startWatch();
    }

    void onShutdown(@Observes ShutdownEvent event) {
        LOG.info("Shutting down Cryostat instance discovery");
        if (watch != null) {
            watch.close();
        }
    }

    private void discoverInstances() {
        try {
            List<Cryostat> crs =
                    k8sClient.resources(Cryostat.class).inAnyNamespace().list().getItems();

            for (Cryostat cr : crs) {
                handleCryostatAdded(cr);
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
                            .resources(Cryostat.class)
                            .inAnyNamespace()
                            .watch(
                                    new Watcher<Cryostat>() {
                                        @Override
                                        public void eventReceived(Action action, Cryostat cr) {
                                            handleWatchEvent(action, cr);
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
            LOG.info("Started watching Cryostat CRs");
        } catch (Exception e) {
            LOG.error("Failed to start watch", e);
        }
    }

    private void handleWatchEvent(Watcher.Action action, Cryostat cr) {
        String namespace = cr.getMetadata().getNamespace();
        String name = cr.getMetadata().getName();

        switch (action) {
            case ADDED:
                LOG.infof("Cryostat CR added: %s/%s", namespace, name);
                handleCryostatAdded(cr);
                break;

            case MODIFIED:
                LOG.infof("Cryostat CR modified: %s/%s", namespace, name);
                handleCryostatModified(cr);
                break;

            case DELETED:
                LOG.infof("Cryostat CR deleted: %s/%s", namespace, name);
                handleCryostatDeleted(cr);
                break;

            case ERROR:
                LOG.errorf("Watch error for CR: %s/%s", namespace, name);
                break;
        }
    }

    private void handleCryostatAdded(Cryostat cr) {
        CryostatInstance instance = toCryostatInstance(cr);
        String key = instanceKey(instance);
        instances.put(key, instance);
        updateNamespaceMapping(instance, null);
    }

    private void handleCryostatModified(Cryostat cr) {
        CryostatInstance newInstance = toCryostatInstance(cr);
        String key = instanceKey(newInstance);
        CryostatInstance oldInstance = instances.get(key);
        instances.put(key, newInstance);
        updateNamespaceMapping(newInstance, oldInstance);
    }

    private void handleCryostatDeleted(Cryostat cr) {
        String namespace = cr.getMetadata().getNamespace();
        String name = cr.getMetadata().getName();
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

    private CryostatInstance toCryostatInstance(Cryostat cr) {
        String namespace = cr.getMetadata().getNamespace();
        String name = cr.getMetadata().getName();

        Set<String> targetNamespaces = new HashSet<>();
        if (cr.getSpec() != null && cr.getSpec().getTargetNamespaces() != null) {
            targetNamespaces.addAll(cr.getSpec().getTargetNamespaces());
        }
        if (targetNamespaces.isEmpty()) {
            targetNamespaces.add(namespace);
        }

        String applicationUrl = determineApplicationUrl(cr);

        return new CryostatInstance(name, namespace, applicationUrl, targetNamespaces);
    }

    private String determineApplicationUrl(Cryostat cr) {
        String namespace = cr.getMetadata().getNamespace();
        String name = cr.getMetadata().getName();

        if (cr.getStatus() != null && cr.getStatus().getApplicationUrl() != null) {
            return cr.getStatus().getApplicationUrl();
        }

        try {
            Service svc = k8sClient.services().inNamespace(namespace).withName(name).get();

            if (svc != null) {
                ServicePort port = findServicePort(svc);
                if (port != null) {
                    boolean tls =
                            port.getAppProtocol() != null && port.getAppProtocol().equals("https");
                    return String.format(
                            "http%s://%s.%s.svc:%d",
                            tls ? "s" : "", name, namespace, port.getPort());
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to determine service URL for %s/%s", namespace, name);
        }

        return String.format("https://%s.%s.svc:8181", name, namespace);
    }

    private ServicePort findServicePort(Service svc) {
        List<ServicePort> ports = svc.getSpec().getPorts();

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

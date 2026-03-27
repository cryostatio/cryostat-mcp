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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.cryostat.mcp.CryostatRESTClient;
import io.cryostat.mcp.model.DiscoveryNode;

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
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CryostatInstanceDiscovery {

    private static final Logger LOG = Logger.getLogger(CryostatInstanceDiscovery.class);
    private static final String LABEL_PART_OF = "app.kubernetes.io/part-of";
    private static final String LABEL_COMPONENT = "app.kubernetes.io/component";
    private static final String CRYOSTAT_PART_OF = "cryostat";
    private static final String CRYOSTAT_COMPONENT = "cryostat";

    private final ExecutorService discoveryExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, CryostatInstance> instances = new ConcurrentHashMap<>();
    private Watch watch;

    @Inject KubernetesClient k8sClient;

    @ConfigProperty(name = "k8s.multi.mcp.authorization.header")
    Optional<String> authorizationHeaderConfig;

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
        }
    }

    private void handleServiceModified(Service svc) {
        CryostatInstance newInstance = toCryostatInstance(svc);
        if (newInstance != null) {
            String key = instanceKey(newInstance);
            instances.put(key, newInstance);
        }
    }

    private void handleServiceDeleted(Service svc) {
        String namespace = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();
        String key = namespace + "/" + name;
        instances.remove(key);
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

        // Determine the application URL
        String applicationUrl = determineApplicationUrl(svc);

        return new CryostatInstance(name, namespace, applicationUrl);
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
        return findByNamespace(namespace, null);
    }

    public Optional<CryostatInstance> findByNamespace(
            String namespace, String authorizationHeader) {
        // Query all Cryostat instances in parallel to find which ones have targets in this
        // namespace
        List<CompletableFuture<CryostatInstance>> futures =
                instances.values().stream()
                        .map(
                                instance ->
                                        CompletableFuture.supplyAsync(
                                                () -> {
                                                    if (instanceHasTargetsInNamespace(
                                                            instance,
                                                            namespace,
                                                            authorizationHeader)) {
                                                        return instance;
                                                    }
                                                    return null;
                                                },
                                                discoveryExecutor))
                        .collect(Collectors.toList());

        // Wait for all queries to complete and collect non-null results
        List<CryostatInstance> matches =
                futures.stream()
                        .map(CompletableFuture::join)
                        .filter(instance -> instance != null)
                        .collect(Collectors.toList());

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

    boolean instanceHasTargetsInNamespace(CryostatInstance instance, String namespace) {
        return instanceHasTargetsInNamespace(instance, namespace, null);
    }

    boolean instanceHasTargetsInNamespace(
            CryostatInstance instance, String namespace, String authorizationHeader) {
        try {
            URI baseUri = URI.create(instance.applicationUrl());
            RestClientBuilder builder = RestClientBuilder.newBuilder().baseUri(baseUri);

            String authHeader =
                    authorizationHeader != null
                            ? authorizationHeader
                            : authorizationHeaderConfig.orElse(null);

            if (authHeader != null && !authHeader.isEmpty()) {
                LOG.debugf(
                        "Using Authorization header for discovery query to %s (source: %s)",
                        instance.applicationUrl(),
                        authorizationHeader != null ? "explicit" : "config");
                builder.register(
                        new ClientHeadersFactory() {
                            @Override
                            public MultivaluedMap<String, String> update(
                                    MultivaluedMap<String, String> incomingHeaders,
                                    MultivaluedMap<String, String> clientOutgoingHeaders) {
                                clientOutgoingHeaders.putSingle("Authorization", authHeader);
                                return clientOutgoingHeaders;
                            }
                        });
            }

            CryostatRESTClient client = builder.build(CryostatRESTClient.class);
            DiscoveryNode tree = client.getDiscoveryTree(true);
            return hasNamespaceNode(tree, namespace);
        } catch (Exception e) {
            LOG.warnf(
                    e,
                    "Failed to query Cryostat instance %s/%s for namespace %s",
                    instance.namespace(),
                    instance.name(),
                    namespace);
            return false;
        }
    }

    private boolean hasNamespaceNode(DiscoveryNode node, String targetNamespace) {
        if ("Namespace".equals(node.nodeType()) && targetNamespace.equals(node.name())) {
            return true;
        }

        if (node.children() != null) {
            for (DiscoveryNode child : node.children()) {
                if (hasNamespaceNode(child, targetNamespace)) {
                    return true;
                }
            }
        }

        return false;
    }

    public Collection<CryostatInstance> getAllInstances() {
        return new ArrayList<>(instances.values());
    }

    private String instanceKey(CryostatInstance instance) {
        return instance.namespace() + "/" + instance.name();
    }
}

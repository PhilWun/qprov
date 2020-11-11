/*******************************************************************************
 * Copyright (c) 2020 the QProv contributors.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package org.quantil.qprov.collector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.quantil.qprov.collector.providers.IBMQProvider;
import org.quantil.qprov.core.entities.QPU_old;
import org.quantil.qprov.core.repositories.QPURepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController()
public class CollectorService {

    private static final Logger logger = LoggerFactory.getLogger(CollectorService.class);

    private final Set<IProvider> availableProviders;

    private final QPURepository qpuRepository;

    @Autowired
    public CollectorService(QPURepository qpuRepository, IBMQProvider ibmqProvider) {
        this.qpuRepository = qpuRepository;
        this.availableProviders = Set.of(ibmqProvider);
    }

    @PostMapping("/collect")
    public Map<String, Boolean> collect(@RequestBody(required = false) List<ProviderCredentials> credentials) {

        credentials.forEach((ProviderCredentials creds) -> logger.debug(creds.toString()));

        final Map<String, Boolean> results = new HashMap<>();
        final Map<String, String> providerCredentials = new HashMap<>();

        credentials.forEach((ProviderCredentials creds) -> providerCredentials.put(creds.getProvider(), creds.getToken()));

        this.availableProviders.forEach((IProvider provider) -> {

            if (provider.preAuthenticationNeeded()) {
                final boolean authenticated = provider.authenticate(providerCredentials.get(provider.getProviderId()));
                if (!authenticated) {
                    logger.debug("Authentication failed for provider {}", provider.getProviderId());

                    // change to continue/for loop
                    return;
                }
                logger.debug("Successfully authenticated to provider {}", provider.getProviderId());
            }

            provider.collectQPUs().forEach(qpuRepository::save);
            logger.debug("QPUs collected");

            results.put(provider.getProviderId(), true);
            logger.info("Successfully collected data from: " + provider.getProviderId());
        });

        logger.debug("QPUs in database:");
        qpuRepository.findAll().forEach((QPU_old qpu) -> logger.debug(qpu.toString()));

        return results;
    }
}

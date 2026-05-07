package com.example.distributed_last_write_wins.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.distributed_last_write_wins.model.CustomerInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicationService {
  private final RestTemplate restTemplate;
  @Autowired
  @Lazy
  private CustomerService customerService;

  @Value("${cluster.peers:}")
  private List<String> peerUrls;

  @Value("${node.url:http://localhost:8080}")
  private String myUrl;

  @Value("${cluster.replication-token:dev-replication-token}")
  private String replicationToken;

  /**
   * Replicate a customer record to all other nodes (Master-Master)
   */
  public void replicateToOthers(CustomerInfo customer) {
    if (peerUrls == null || peerUrls.isEmpty()) {
      log.warn("No peer URLs configured - replication disabled");
      return;
    }

    for (String peerUrl : peerUrls) {
      if (peerUrl.equals(myUrl)) {
        continue;
      }

      try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Replication-Token", replicationToken);
        HttpEntity<CustomerInfo> request = new HttpEntity<>(customer, headers);

        String replicateUrl = peerUrl + "/api/replicate";
        restTemplate.postForObject(replicateUrl, request, Void.class);
        log.info("Replicated customer CID={} to peer {}",
            customer.getCid(), peerUrl);
      } catch (Exception e) {
        log.error("Failed to replicate CID={} to {}: {}",
            customer.getCid(), peerUrl, e.getMessage());
        // In production: add to retry queue
      }
    }
  }

  /**
   * Receive replication from another node
   * Apply LWW without re-replicating (avoid infinite loop)
   */
  public void receiveReplication(CustomerInfo customer) {
    log.info("Received replication: CID={}, Address={}, Phone={}, Timestamp={}, SourceNode={}",
        customer.getCid(), customer.getAddress(), customer.getPhone(),
        customer.getUpdateTimestamp(), customer.getLastUpdatedByNode());

    // Upsert without replicating back.
    customerService.upsertWithTimestamp(customer, true);
  }
}

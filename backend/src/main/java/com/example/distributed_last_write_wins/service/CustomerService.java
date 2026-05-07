package com.example.distributed_last_write_wins.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.distributed_last_write_wins.model.CustomerInfo;
import com.example.distributed_last_write_wins.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
  private final CustomerRepository repository;
  private final ClockSkewService clockSkewService;
  private final ReplicationService replicationService;

  @Value("${node.id:1}")
  private String nodeId;

  /**
   * Create or update customer information using LWW
   * This is the main method called by the API
   */
  public CustomerInfo upsert(CustomerInfo customer) {
    // Add timestamp from this node's skewed clock
    long timestamp = clockSkewService.getCurrentTimestamp();
    customer.setUpdateTimestamp(timestamp);
    customer.setLastUpdatedByNode(nodeId);

    return upsertWithTimestamp(customer, false);
  }

  /**
   * Internal method for upsert with explicit timestamp control
   * 
   * @param customer      The customer data
   * @param isReplication Whether this call comes from replication (to avoid
   *                      loops)
   */
  public CustomerInfo upsertWithTimestamp(CustomerInfo customer, boolean isReplication) {
    String cid = customer.getCid();
    Optional<CustomerInfo> existingOpt = repository.findById(cid);

    CustomerInfo result;

    if (existingOpt.isEmpty()) {
      // New customer - create
      customer.setVersion(1);
      result = repository.save(customer);
      log.info(" NEW CUSTOMER: CID={}, Address={}, Phone={}, Timestamp={}, Node={}",
          cid, customer.getAddress(), customer.getPhone(),
          customer.getUpdateTimestamp(), nodeId);
    } else {
      CustomerInfo existing = existingOpt.get();
      long incomingTimestamp = customer.getUpdateTimestamp();
      long existingTimestamp = existing.getUpdateTimestamp();
      String incomingNode = customer.getLastUpdatedByNode() == null ? nodeId : customer.getLastUpdatedByNode();
      String existingNode = existing.getLastUpdatedByNode() == null ? "" : existing.getLastUpdatedByNode();
      String oldAddress = existing.getAddress();
      String oldPhone = existing.getPhone();
      boolean incomingWins = incomingTimestamp > existingTimestamp
          || (incomingTimestamp == existingTimestamp && incomingNode.compareTo(existingNode) > 0);

      // LWW: Compare timestamps - larger wins
      if (incomingWins) {
        // Newer update - overwrite. If timestamps tie, use node id as a deterministic tie-breaker.
        existing.setAddress(customer.getAddress());
        existing.setPhone(customer.getPhone());
        existing.setUpdateTimestamp(incomingTimestamp);
        existing.setLastUpdatedByNode(incomingNode);
        existing.setVersion(existing.getVersion() + 1);
        result = repository.save(existing);

        log.info("UPDATED (LWW wins): CID={}, OldAddress={}, NewAddress={}, OldPhone={}, NewPhone={}, "
            + "OldTs={}, NewTs={}, WinnerNode={}",
            cid, oldAddress, customer.getAddress(), oldPhone, customer.getPhone(),
            existingTimestamp, incomingTimestamp, incomingNode);
      } else {
        // Older update - reject (this demonstrates clock skew problem).
        result = existing;
        log.warn("REJECTED (LWW loses): CID={}, Address={}, "
            + "IncomingTs={}, ExistingTs={}, IncomingNode={}, ExistingNode={}",
            cid, customer.getAddress(),
            incomingTimestamp, existingTimestamp, incomingNode, existingNode);

        // This is the DANGER of clock skew!
        if (!isReplication && Math.abs(clockSkewService.getSkewSeconds()) > 0) {
          log.warn("CLOCK SKEW DANGER: A newer real-time update may have been "
              + "discarded because this node's clock is {}!",
              clockSkewService.getSkewDescription());
        }
      }
    }

    // Replicate to other nodes (but not if this is a replication message)
    if (!isReplication) {
      replicationService.replicateToOthers(result);
    }

    return result;
  }

  /**
   * Find customer by CID
   */
  public Optional<CustomerInfo> findByCid(String cid) {
    return repository.findById(cid);
  }

  /**
   * Get all customers
   */
  public Iterable<CustomerInfo> findAll() {
    return repository.findAll();
  }

  /**
   * Delete all customers (for testing)
   */
  public void deleteAll() {
    repository.deleteAll();
    log.info("Deleted all customer records");
  }

  /**
   * Get count of customers
   */
  public long count() {
    return repository.count();
  }
}

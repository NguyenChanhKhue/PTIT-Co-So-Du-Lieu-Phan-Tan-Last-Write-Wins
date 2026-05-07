package com.example.distributed_last_write_wins.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.distributed_last_write_wins.model.CustomerInfo;
import com.example.distributed_last_write_wins.service.ClockSkewService;
import com.example.distributed_last_write_wins.service.CustomerService;
import com.example.distributed_last_write_wins.service.ReplicationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allow React frontend to call
public class CustomerController {
  private final CustomerService customerService;
  private final ReplicationService replicationService;
  private final ClockSkewService clockSkewService;

  @Value("${node.id:1}")
  private String nodeId;

  @Value("${cluster.replication-token:dev-replication-token}")
  private String replicationToken;

  /**
   * Create or update customer information
   * POST /api/customer
   * Body: {"cid":"C001", "address":"123 Main St", "phone":"555-0101"}
   */
  @PostMapping("/api/customer")
  public ResponseEntity<CustomerInfo> createOrUpdate(@RequestBody CustomerInfo customer) {
    if (customer.getCid() == null || customer.getCid().trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    // Default empty strings if null
    if (customer.getAddress() == null)
      customer.setAddress("");
    if (customer.getPhone() == null)
      customer.setPhone("");

    CustomerInfo result = customerService.upsert(customer);
    return ResponseEntity.ok(result);
  }

  /**
   * Get customer by CID
   * GET /api/customer/{cid}
   */
  @GetMapping("/api/customer/{cid}")
  public ResponseEntity<CustomerInfo> getCustomer(@PathVariable String cid) {
    Optional<CustomerInfo> customer = customerService.findByCid(cid);
    return customer.map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Get all customers
   * GET /api/customers
   */
  @GetMapping("/api/customers")
  public ResponseEntity<Iterable<CustomerInfo>> getAllCustomers() {
    return ResponseEntity.ok(customerService.findAll());
  }

  /**
   * Delete all customers (for testing)
   * DELETE /api/customers
   */
  @DeleteMapping("/api/customers")
  public ResponseEntity<Void> deleteAll() {
    customerService.deleteAll();
    return ResponseEntity.ok().build();
  }

  /**
   * Replication endpoint (internal use only)
   * POST /api/replicate
   */
  @PostMapping("/api/replicate")
  public ResponseEntity<Void> replicate(
      @RequestHeader(value = "X-Replication-Token", required = false) String token,
      @RequestBody CustomerInfo customer) {
    if (!replicationToken.equals(token)) {
      return ResponseEntity.status(403).build();
    }
    replicationService.receiveReplication(customer);
    return ResponseEntity.ok().build();
  }

  /**
   * Get node information including clock skew status
   * GET /api/node/info
   */
  @GetMapping("/api/node/info")
  public ResponseEntity<Map<String, Object>> getNodeInfo() {
    Map<String, Object> info = new HashMap<>();
    info.put("nodeId", nodeId);
    info.put("clockSkewSeconds", clockSkewService.getSkewSeconds());
    info.put("clockSkewDescription", clockSkewService.getSkewDescription());
    info.put("dangerWarning", clockSkewService.getDangerWarning());
    info.put("currentSkewedTimestamp", clockSkewService.getCurrentTimestamp());
    info.put("currentRealTimestamp", clockSkewService.getRealTimestamp());
    info.put("customerCount", customerService.count());
    return ResponseEntity.ok(info);
  }

  /**
   * Health check endpoint
   * GET /api/health
   */
  @GetMapping("/api/health")
  public ResponseEntity<Map<String, String>> health() {
    Map<String, String> health = new HashMap<>();
    health.put("status", "UP");
    health.put("node", nodeId);
    health.put("clockSkew", String.valueOf(clockSkewService.getSkewSeconds()));
    return ResponseEntity.ok(health);
  }

  /**
   * Demo scenario: Show clock skew danger
   * POST /api/demo/clock-skew-danger
   * This demonstrates the problem described in the assignment
   */
  @PostMapping("/api/demo/clock-skew-danger")
  public ResponseEntity<Map<String, Object>> demonstrateClockSkewDanger(
      @RequestBody Map<String, String> request) {

    Map<String, Object> result = new HashMap<>();
    result.put("scenario", "Clock Skew Danger Demonstration");
    result.put("nodeId", nodeId);
    result.put("cid", request.getOrDefault("cid", "DEMO_C001"));
    result.put("address", request.getOrDefault("address", "Demo Address"));
    result.put("phone", request.getOrDefault("phone", "000-0000"));
    result.put("clockSkew", clockSkewService.getSkewSeconds());
    result.put("warning", clockSkewService.getDangerWarning());
    result.put("message", "This node simulates a " +
        (clockSkewService.getSkewSeconds() > 0 ? "FAST" : (clockSkewService.getSkewSeconds() < 0 ? "SLOW" : "NORMAL")) +
        " clock. Updates may be incorrectly accepted or rejected!");

    return ResponseEntity.ok(result);
  }
}

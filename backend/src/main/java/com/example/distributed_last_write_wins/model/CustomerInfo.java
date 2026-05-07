package com.example.distributed_last_write_wins.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInfo implements Serializable {
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
      .ofPattern("yyyy-MM-dd HH:mm:ss")
      .withZone(ZoneId.systemDefault());

  @Id
  @Column(name = "cid", nullable = false)
  private String cid; // Customer ID (Primary Key)

  @Column(name = "address", columnDefinition = "TEXT")
  private String address; // Customer Address

  @Column(name = "phone", columnDefinition = "TEXT")
  private String phone; // Customer Phone Number

  @Column(name = "update_timestamp", nullable = false)
  private Long updateTimestamp; // Used for LWW conflict resolution

  @Column(name = "last_updated_by")
  private String lastUpdatedByNode; // Which node last updated this record

  @Column(name = "version")
  private Integer version = 1; // Version number for tracking

  // Helper method to check if this record is newer than another
  public boolean isNewerThan(CustomerInfo other) {
    if (other == null)
      return true;
    if (this.updateTimestamp == null)
      return false;
    if (other.updateTimestamp == null)
      return true;
    if (!this.updateTimestamp.equals(other.updateTimestamp)) {
      return this.updateTimestamp > other.updateTimestamp;
    }

    String thisNode = this.lastUpdatedByNode == null ? "" : this.lastUpdatedByNode;
    String otherNode = other.lastUpdatedByNode == null ? "" : other.lastUpdatedByNode;
    return thisNode.compareTo(otherNode) > 0;
  }

  @Transient
  @JsonProperty("updateTimestampReadable")
  public String getUpdateTimestampReadable() {
    if (updateTimestamp == null) {
      return null;
    }
    return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(updateTimestamp));
  }
}

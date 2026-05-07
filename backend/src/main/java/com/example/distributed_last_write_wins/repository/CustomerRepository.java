package com.example.distributed_last_write_wins.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.distributed_last_write_wins.model.CustomerInfo;

public interface CustomerRepository extends JpaRepository<CustomerInfo, String> {

}

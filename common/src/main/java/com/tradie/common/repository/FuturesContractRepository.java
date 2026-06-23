package com.tradie.common.repository;

import com.tradie.common.entity.FuturesContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FuturesContractRepository extends JpaRepository<FuturesContract, String> {

    List<FuturesContract> findBySymbolAndActiveTrue(String symbol);

    Optional<FuturesContract> findBySymbolAndFrontMonthTrue(String symbol);

    List<FuturesContract> findByActiveTrue();
}

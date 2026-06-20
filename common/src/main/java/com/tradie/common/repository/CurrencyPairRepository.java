package com.tradie.common.repository;

import com.tradie.common.entity.CurrencyPair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurrencyPairRepository extends JpaRepository<CurrencyPair, String> {

    List<CurrencyPair> findByCategory(String category);
}

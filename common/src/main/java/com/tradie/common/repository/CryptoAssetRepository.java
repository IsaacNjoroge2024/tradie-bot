package com.tradie.common.repository;

import com.tradie.common.entity.CryptoAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CryptoAssetRepository extends JpaRepository<CryptoAsset, String> {

    Optional<CryptoAsset> findBySymbolAndAvailableOnIbkrTrue(String symbol);

    List<CryptoAsset> findByAvailableOnIbkrTrue();
}

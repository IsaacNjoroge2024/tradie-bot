package com.tradie.common.repository;

import com.tradie.common.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    long countByStatus(Position.PositionStatus status);

    List<Position> findByStatus(Position.PositionStatus status);
}

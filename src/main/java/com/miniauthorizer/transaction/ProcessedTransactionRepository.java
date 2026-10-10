package com.miniauthorizer.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransaction, ProcessedTransactionId> {

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProcessedTransaction p where p.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}

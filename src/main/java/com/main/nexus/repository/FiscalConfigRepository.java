package com.main.nexus.repository;

import com.main.nexus.model.FiscalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Singleton: sempre acessado por FiscalConfig.SINGLETON_ID (ver NfseService.getConfig).
@Repository
public interface FiscalConfigRepository extends JpaRepository<FiscalConfig, Long> {
}

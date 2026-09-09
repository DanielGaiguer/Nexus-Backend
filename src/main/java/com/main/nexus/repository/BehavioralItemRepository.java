package com.main.nexus.repository;

import com.main.nexus.model.BehavioralItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BehavioralItemRepository extends JpaRepository<BehavioralItem, Long> {

    // Os itens que uma etapa BEHAVIORAL nova recebe, na ordem de exibição semeada (dimensões
    // intercaladas, ver BehavioralItemSeed).
    List<BehavioralItem> findByActiveTrueOrderByOrderIndexAsc();
}

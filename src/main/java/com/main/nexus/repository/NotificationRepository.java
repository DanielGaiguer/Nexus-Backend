package com.main.nexus.repository;

import com.main.nexus.model.Notification;
import com.main.nexus.model.enums.NotificationType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Todas as notificações de um usuário, mais recentes primeiro
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Só as não lidas
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    // Contagem de não lidas — usada no badge do sino
    long countByUserIdAndReadFalse(Long userId);

    // Verifica se já existe notificação desse tipo/actionUrl para o usuário — evita duplicidade
    boolean existsByUserIdAndTypeAndActionUrl(Long userId, NotificationType type, String actionUrl);

    // Marca todas as notificações de um usuário como lidas
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    // Marca uma notificação específica como lida
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.user.id = :userId")
    void markAsReadByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // Remove notificações antigas — útil para limpeza periódica
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.read = true AND n.createdAt < :before")
    void deleteOldReadNotifications(
            @Param("userId") Long userId,
            @Param("before") java.time.LocalDateTime before);
}
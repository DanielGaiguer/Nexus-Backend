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

    // Badge de sidebar do "Padrão B": quantos eventos (destes tipos) chegaram
    // para o usuário depois que ele viu a seção pela última vez. Usa o feed de
    // notificações só como log de eventos -- o "visto" fica em SectionView, não
    // no flag `read` (que é do sino). Ver SidebarBadgeService.
    long countByUserIdAndTypeInAndCreatedAtAfter(
            Long userId, java.util.Collection<NotificationType> types,
            java.time.LocalDateTime after);

    // Mesma contagem do método acima, mas resolvendo o "visto" da seção
    // (SectionView.seenAt, ou :epoch se ainda não houver linha) na própria
    // query -- antes eram 2 idas ao banco por seção em SidebarBadgeService.
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.user.id = :userId
              AND n.type IN :types
              AND n.createdAt > COALESCE(
                    (SELECT sv.seenAt FROM SectionView sv
                     WHERE sv.user.id = :userId AND sv.section = :section),
                    :epoch)
            """)
    long countUnseenForSection(
            @Param("userId") Long userId,
            @Param("types") java.util.Collection<NotificationType> types,
            @Param("section") com.main.nexus.model.enums.SidebarSection section,
            @Param("epoch") java.time.LocalDateTime epoch);

    // Verifica se já existe notificação desse tipo
    boolean existsByUserIdAndTypeAndActionUrl(Long userId, NotificationType type, String actionUrl);

    // Marca todas as notificações de um usuário como lidas
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    // Marca uma notificação específica como lida
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.user.id = :userId")
    void markAsReadByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // Remove notificações antigas de todo mundo — usada pelo job noturno
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.read = true AND n.createdAt < :before")
    void deleteAllOldReadNotifications(@Param("before") java.time.LocalDateTime before);

    // LGPD -- exclusão de conta: notificações são dado pessoal do próprio
    // usuário, sem função de integridade para terceiros -> apagadas.
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
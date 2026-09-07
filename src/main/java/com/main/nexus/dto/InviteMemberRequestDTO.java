package com.main.nexus.dto;

// Corpo de POST /api/company/members/invite -- só o e-mail alvo. O papel é
// sempre MEMBER (não há convite de OWNER).
public record InviteMemberRequestDTO(String email) {}

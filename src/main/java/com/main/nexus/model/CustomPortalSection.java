package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

// Seção institucional extra da plataforma personalizada (ex.: "Nossos valores",
// "Benefícios"). Lista reordenável em CustomPortal.sections (@ElementCollection
// com @OrderColumn) — a ordem é a posição no List.
@Embeddable
public class CustomPortalSection {

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    public CustomPortalSection() {
    }

    public CustomPortalSection(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomPortalSection other)) return false;
        return Objects.equals(title, other.title) && Objects.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, content);
    }
}

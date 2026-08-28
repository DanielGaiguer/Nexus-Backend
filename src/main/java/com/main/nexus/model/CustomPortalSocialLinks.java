package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// Links de redes sociais da plataforma personalizada — exibidos no rodapé da
// página pública. Tudo opcional; guardado como colunas em tb_custom_portal.
@Embeddable
public class CustomPortalSocialLinks {

    @Column(name = "social_website", length = 300)
    private String website;

    @Column(name = "social_linkedin", length = 300)
    private String linkedin;

    @Column(name = "social_instagram", length = 300)
    private String instagram;

    @Column(name = "social_facebook", length = 300)
    private String facebook;

    @Column(name = "social_youtube", length = 300)
    private String youtube;

    @Column(name = "social_x", length = 300)
    private String x;

    @Column(name = "social_github", length = 300)
    private String github;

    public boolean isEmpty() {
        return blank(website) && blank(linkedin) && blank(instagram)
                && blank(facebook) && blank(youtube) && blank(x) && blank(github);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getLinkedin() {
        return linkedin;
    }

    public void setLinkedin(String linkedin) {
        this.linkedin = linkedin;
    }

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }

    public String getFacebook() {
        return facebook;
    }

    public void setFacebook(String facebook) {
        this.facebook = facebook;
    }

    public String getYoutube() {
        return youtube;
    }

    public void setYoutube(String youtube) {
        this.youtube = youtube;
    }

    public String getX() {
        return x;
    }

    public void setX(String x) {
        this.x = x;
    }

    public String getGithub() {
        return github;
    }

    public void setGithub(String github) {
        this.github = github;
    }
}

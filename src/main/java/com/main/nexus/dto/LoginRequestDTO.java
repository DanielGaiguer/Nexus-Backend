/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
// LoginRequestDTO.java
package com.main.nexus.dto;

public record LoginRequestDTO(
        String email,
        String password
) {}
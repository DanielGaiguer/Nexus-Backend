/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.main.nexus;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GerarHashSenha {
 
    public static void main(String[] args) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
 
        String senhaPadraoTeste = "123456";
        String hash = passwordEncoder.encode(senhaPadraoTeste);
 
        System.out.println("Senha em texto puro: " + senhaPadraoTeste);
        System.out.println("Hash BCrypt gerado : " + hash);
        System.out.println();
        System.out.println("Confirma que o hash bate com a senha: "
                + passwordEncoder.matches(senhaPadraoTeste, hash));
 
        // Caso queira hashes diferentes por usuario, basta chamar encode()
        // novamente para cada senha - o BCrypt gera um salt novo a cada chamada,
        // entao o hash sera diferente mesmo para a mesma senha em texto puro.
    }
}
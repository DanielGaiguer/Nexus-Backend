-- Migração: technologies (String) -> tb_previous_project_technologies (List<String>)
-- NÃO executar automaticamente. Revisar e rodar manualmente em cada ambiente.

-- Passo 1: criar a nova tabela de tecnologias
CREATE TABLE tb_previous_project_technologies (
    previous_project_id BIGINT NOT NULL,
    technology          VARCHAR(100) NOT NULL,
    FOREIGN KEY (previous_project_id)
        REFERENCES tb_previous_project(id) ON DELETE CASCADE
);

-- Passo 2: migrar dados existentes (cada tecnologia separada por vírgula
-- vira uma linha na nova tabela)
-- ATENÇÃO: executar apenas se houver dados existentes no banco
-- Este script assume que as tecnologias estavam separadas por vírgula
INSERT INTO tb_previous_project_technologies (previous_project_id, technology)
SELECT id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(technologies, ',', n.n), ',', -1))
FROM tb_previous_project
CROSS JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
    UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8
) n
WHERE n.n <= 1 + (LENGTH(technologies) - LENGTH(REPLACE(technologies, ',', '')))
  AND technologies IS NOT NULL AND technologies != '';

-- Passo 3: remover a coluna antiga
ALTER TABLE tb_previous_project DROP COLUMN technologies;

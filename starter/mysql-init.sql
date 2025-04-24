-- 设置 root 密码
SET PASSWORD FOR 'root'@'localhost' = PASSWORD('r8FqfdbWUaN3');
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY 'r8FqfdbWUaN3' WITH GRANT OPTION;
FLUSH PRIVILEGES;

-- 创建 bytedesk 数据库
CREATE DATABASE IF NOT EXISTS `bytedesk` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `bytedesk`;

-- 可以在这里添加更多的初始化SQL语句，如创建表和插入初始数据 
-- docker-entrypoint 는 MYSQL_DATABASE 가 지정되면 해당 DB를 기본 데이터베이스로 선택해 SQL 을 실행합니다.
-- 따라서 DATABASE() 값을 이용하면 .env 의 MYSQL_DATABASE 값을 init.sql 에서도 따라갈 수 있습니다.
SELECT DATABASE() INTO @app_db_name;

-- 애플리케이션용 계정을 생성합니다.
-- '%'는 모든 호스트에서의 접근을 허용합니다.
CREATE USER IF NOT EXISTS 'myuser'@'%' IDENTIFIED BY 'mypassword';
ALTER USER 'myuser'@'%' IDENTIFIED BY 'mypassword';

-- 현재 기본 데이터베이스에 모든 권한을 부여합니다.
SET @grant_sql = CONCAT(
  'GRANT ALL PRIVILEGES ON `',
  REPLACE(@app_db_name, '`', '``'),
  '`.* TO ''myuser''@''%'''
);
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
FLUSH PRIVILEGES;

-- 필요 시, 초기 테이블 생성 및 데이터 삽입 쿼리를 추가할 수 있습니다.

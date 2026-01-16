#!/bin/bash

# Запуск WildFly в фоне
/opt/jboss/wildfly/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 &
WILDFLY_PID=$!

# Ожидание запуска WildFly
echo "Waiting for WildFly to start..."
for i in {1..60}; do
    if /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q "running"; then
        echo "WildFly is running"
        break
    fi
    sleep 2
done

# Установка модулей
echo "Installing PostgreSQL driver module..."
curl -o /tmp/postgresql.jar https://jdbc.postgresql.org/download/postgresql-42.7.1.jar
/opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="module add --name=org.postgresql --resources=/tmp/postgresql.jar --dependencies=javax.api,javax.transaction.api" || echo "Module add failed, trying alternative method"
rm -f /tmp/postgresql.jar

# Установка EclipseLink
echo "Installing EclipseLink..."
mkdir -p /opt/jboss/wildfly/modules/org/eclipse/persistence/main
curl -L -o /opt/jboss/wildfly/modules/org/eclipse/persistence/main/eclipselink.jar https://repo1.maven.org/maven2/org/eclipse/persistence/eclipselink/4.0.2/eclipselink-4.0.2.jar
cat > /opt/jboss/wildfly/modules/org/eclipse/persistence/main/module.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.9" name="org.eclipse.persistence">
    <resources>
        <resource-root path="eclipselink.jar"/>
    </resources>
    <dependencies>
        <module name="javax.api"/>
        <module name="javax.transaction.api"/>
        <module name="javax.persistence.api"/>
        <module name="org.jboss.logging"/>
        <module name="jakarta.ws.rs.api"/>
        <module name="jakarta.xml.bind.api"/>
    </dependencies>
</module>
EOF

# Настройка datasource
echo "Configuring datasource..."

# Удаляем deployment, если он есть, чтобы создать datasource без конфликтов
/opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/deployment=IS-lab1.war:remove" 2>/dev/null || true
sleep 2

# Проверяем, существует ли уже datasource, и удаляем его если есть (чтобы пересоздать с правильными параметрами)
/opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:read-resource" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "Existing datasource found, removing it to recreate with correct parameters..."
    /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:remove" 2>/dev/null || true
    sleep 2
fi

# Проверяем, что datasource удален, и создаем новый
/opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:read-resource" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "Creating datasource..."
    # Используем переменные окружения для создания datasource
    DB_HOST="${DB_HOST:-postgres}"
    DB_PORT="${DB_PORT:-5432}"
    DB_NAME="${DB_NAME:-studs}"
    DB_USER="${DB_USER:-postgres}"
    DB_PASSWORD="${DB_PASSWORD:-postgres}"
    DB_MAX_POOL="${DB_MAX_POOL:-10}"
    
    echo "Using database connection: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} as ${DB_USER}"
    echo "Environment variables:"
    echo "  DB_URL=${DB_URL}"
    echo "  DB_HOST=${DB_HOST}"
    echo "  DB_PORT=${DB_PORT}"
    echo "  DB_NAME=${DB_NAME}"
    echo "  DB_USER=${DB_USER}"
    echo "  DB_PASSWORD=${DB_PASSWORD}"
    
    # Создаем JDBC драйвер, если его еще нет
    /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/jdbc-driver=postgresql:read-resource" 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "Adding PostgreSQL JDBC driver..."
        /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/jdbc-driver=postgresql:add(driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver)" || echo "Driver may already exist"
        sleep 1
    fi
    
    # Создаем datasource с переменными окружения (одной строкой, чтобы избежать проблем с heredoc)
    DS_CMD="/subsystem=datasources/data-source=PostgresDS:add(jndi-name=java:/jboss/datasources/PostgresDS,driver-name=postgresql,connection-url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME},user-name=${DB_USER},password=${DB_PASSWORD},enabled=true,min-pool-size=5,max-pool-size=${DB_MAX_POOL})"
    
    echo "Executing datasource creation command..."
    /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="$DS_CMD"
    
    if [ $? -eq 0 ]; then
        echo "✓ Datasource created successfully"
    else
        echo "Warning: Failed to create datasource via command, trying CLI file method..."
        # Резервный метод: создаем временный CLI файл с правильными значениями
        cat > /tmp/datasource.cli <<EOF
/subsystem=datasources/jdbc-driver=postgresql:add(driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver)
/subsystem=datasources/data-source=PostgresDS:add(jndi-name=java:/jboss/datasources/PostgresDS,driver-name=postgresql,connection-url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME},user-name=${DB_USER},password=${DB_PASSWORD},enabled=true,min-pool-size=5,max-pool-size=${DB_MAX_POOL})
EOF
        /opt/jboss/wildfly/bin/jboss-cli.sh --connect --file=/tmp/datasource.cli || {
            echo "CLI file method also failed, trying fallback..."
            /opt/jboss/wildfly/bin/jboss-cli.sh --connect --file=/opt/jboss/wildfly/bin/standalone-datasource.cli || echo "All methods failed"
        }
        rm -f /tmp/datasource.cli
    fi
    sleep 2
else
    echo "Datasource already exists"
fi

# Проверяем, что datasource создан и выводим его параметры
    echo "Verifying datasource configuration..."
    /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:read-resource" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✓ Datasource configuration verified"
        echo "Datasource details:"
        CONN_URL=$(/opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:read-attribute(name=connection-url)" 2>/dev/null | grep -o '"result" => "[^"]*"' | cut -d'"' -f4)
        CONN_USER=$(/opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:read-attribute(name=user-name)" 2>/dev/null | grep -o '"result" => "[^"]*"' | cut -d'"' -f4)
        echo "  Connection URL: $CONN_URL"
        echo "  User name: $CONN_USER"
        echo ""
        echo "Testing datasource connection..."
        /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:test-connection-in-pool" 2>&1 | head -5
    else
        echo "✗ ERROR: Datasource configuration failed!"
        echo "Attempting to test datasource connection..."
        /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=datasources/data-source=PostgresDS:test-connection-in-pool" 2>&1 || echo "Connection test failed"
    fi

# Деплой WAR файла
echo "Deploying WAR file..."
# Проверяем, есть ли предсобранный WAR файл
if [ -f /tmp/war/IS-lab1.war ]; then
    echo "✓ Found pre-built WAR file at /tmp/war/IS-lab1.war"
    # Проверяем размер файла
    WAR_SIZE=$(stat -c%s /tmp/war/IS-lab1.war 2>/dev/null || stat -f%z /tmp/war/IS-lab1.war 2>/dev/null || echo "unknown")
    echo "  WAR file size: $WAR_SIZE bytes"
    cp /tmp/war/IS-lab1.war /opt/jboss/wildfly/standalone/deployments/IS-lab1.war
    echo "  Copied to deployments directory"
elif [ -f /opt/jboss/wildfly/standalone/deployments/IS-lab1.war ]; then
    echo "WAR file already exists in deployments directory"
else
    echo "✗ ERROR: No pre-built WAR file found at /tmp/war/IS-lab1.war"
    echo "  Please build the WAR file first using: gradle clean build"
    echo "  Expected location: build/libs/IS-lab1.war"
    echo ""
    echo "  Listing /tmp/war directory:"
    ls -la /tmp/war/ 2>/dev/null || echo "  Directory /tmp/war does not exist or is empty"
    exit 1
fi

# Ждем, пока WildFly обнаружит новый WAR файл
sleep 3

# Проверяем статус деплоя
echo "Checking deployment status..."
DEPLOYMENT_OK=false
for i in {1..30}; do
    DEPLOYMENT_INFO=$(/opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/deployment=IS-lab1.war:read-resource" 2>/dev/null)
    if [ $? -eq 0 ]; then
        STATUS=$(echo "$DEPLOYMENT_INFO" | grep -o '"status" => "[^"]*"' | cut -d'"' -f4)
        if [ "$STATUS" = "OK" ]; then
            echo "✓ Deployment successful! Status: OK"
            DEPLOYMENT_OK=true
            break
        elif [ "$STATUS" = "FAILED" ]; then
            echo "✗ Deployment FAILED!"
            echo "Deployment details:"
            echo "$DEPLOYMENT_INFO" | head -20
            echo ""
            echo "Last 50 lines of server log:"
            tail -50 /opt/jboss/wildfly/standalone/log/server.log 2>/dev/null | grep -i -E "(error|exception|failed|deploy)" || tail -50 /opt/jboss/wildfly/standalone/log/server.log 2>/dev/null
            break
        else
            echo "Deployment status: $STATUS (waiting... $i/30)"
        fi
    else
        echo "Waiting for deployment to be recognized... ($i/30)"
    fi
    sleep 2
done

if [ "$DEPLOYMENT_OK" = "false" ]; then
    echo ""
    echo "=========================================="
    echo "WARNING: Deployment may have failed!"
    echo "Check the logs above for details."
    echo "You can also check logs with: docker logs is-lab1-wildfly"
    echo "=========================================="
    echo ""
    echo "Checking if WAR file exists in deployments:"
    ls -la /opt/jboss/wildfly/standalone/deployments/IS-lab1.war* 2>/dev/null || echo "  No WAR file found"
    echo ""
    echo "Listing all deployments:"
    /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/deployment:read-resource" 2>/dev/null | head -20 || echo "  Failed to list deployments"
else
    echo ""
    echo "=========================================="
    echo "✓ Deployment successful!"
    echo "=========================================="
    echo ""
    echo "Application URLs:"
    echo "  Frontend: http://localhost:8080/IS-lab1/"
    echo "  API: http://localhost:8080/IS-lab1/api/routes"
    echo "  Management Console: http://localhost:9990 (admin/admin)"
    echo ""
fi

echo "WildFly is running. Frontend available at http://localhost:8080/IS-lab1/"

# Ожидание завершения
wait $WILDFLY_PID


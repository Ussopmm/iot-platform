# **IOT Platform**

Что бы подробнее ознакомиться с архитектурой системы рекомендуется рассмотреть C4 UML диаграммы.

Для просмотра перейдите в директорию [diagrams](./diagrams/).

**context.puml** описывает общий контекст системы. Показывает систему целиком и взаимодействия данной системы с
внешними пользователями и системами

[Контекст системы (context.puml)](./diagrams/context.puml)

**containers.puml** описывает внутреннее устройство системы, сервисы, базы данных, брокеры и т.д. и их
взаимодействие друг с другом

[Контейнеры системы (containers.puml)](./diagrams/containers.puml)

**ecs_component.puml** - описывает внутреннее устройство сервиса Event Collector Service

[Компоненты системы (ecs_component.puml)](./diagrams/ecs-diagrams/ecs_component.puml)

**processing_sequence.puml** - диаграмма последовательности обработки события от момента получения события
до момента записи в базу данных

[Sequence diagram (processing_sequence.puml)](./diagrams/ecs-diagrams/processing_sequence.puml)

**dcs_component.puml** - описывает внутреннее устройство сервиса Device Collector Service

[Компоненты системы (dcs_component.puml)](./diagrams/dcs-diagrams/dcs_component.puml)

**dcs_sequence_diagram.puml** - диаграмма последовательности обработки данных устройства от момента получения данных
до момента записи в базу данных с учетом возможных ошибок

[Sequence diagram (dcs_sequence_diagram.puml)](./diagrams/dcs-diagrams/dcs_sequence_diagram.puml)

# Инструкция по запуску системы

## Запуск системы

1. Перейдите в папку **infrastructure**:
```
cd infrastructure
```
2. Запустите контейнеры:
```
docker-compose up -d --build
```
Или через Makefile:
```
make up
```
3. Проверьте, что все контейнеры работают и имеют статус healthy:
```
docker ps | make ps
```
4. Перейдите по пути http://localhost:8080 что бы увидеть интерфейс кейклока,
   http://localhost:3000, чтобы увидеть интерфейс Grafana. И если, все успешно открывается,
значит вы все правильно запустили
5. Чтобы остановить контейнеры:
```
docker-compose down | make down
``` 


## Добавление источников данных

```
Откройте интерфейс Grafana: http://localhost:3000
Перейдите:
Connections → Data sources → Add new data source
Выберите Prometheus и укажите URL:
http://prometheus:9090
```

Аналогично можно добавить Loki и Tempo, если вы хотите их использовать.


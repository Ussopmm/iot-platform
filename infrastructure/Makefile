up:
	@echo("Starting local infrastructure...")
	docker-compose up -d --build

down:
	@echo("Stopping local infrastructure...")
	docker-compose down

logs:
	@echo("Showing logs...")
	docker-compose logs -f

ps:
	docker-compose ps

reset:
	@echo("Resetting local infrastructure...")
	docker-compose down -v
	docker-compose up --build -d
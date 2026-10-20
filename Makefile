# One-word commands (root CLAUDE.md section 7). Targets for parts not built yet say so.
.PHONY: setup data train export-model test test-e2e record-fallback up demo prod-up prod-down prod-ps prod-logs prod-check

setup:
	pip install -r ml/requirements.txt
	@test -f backend/requirements.txt && pip install -r backend/requirements.txt || echo "backend: not built yet"
	@test -f simulator/requirements.txt && pip install -r simulator/requirements.txt || echo "simulator: not built yet"
	pip install -r fraudintel/requirements.txt
	@test -f dashboard/package.json && (cd dashboard && npm install) || echo "dashboard: not built yet"

data:
	python -I ml/src/features/dataset/synthetic.py
	python -I ml/src/features/dataset/public.py

train: data
	python -I ml/src/features/train/train.py
	python -I ml/src/features/evaluate/evaluate.py

export-model:
	python -I ml/src/features/export/export.py

test:
	python -m pytest ml/tests -v
	@test -d backend/tests && ls backend/tests/test_*.py >/dev/null 2>&1 && python -m pytest backend/tests -v || echo "backend tests: none yet"
	@test -d simulator/tests && ls simulator/tests/test_*.py >/dev/null 2>&1 && python -m pytest simulator/tests -v || echo "simulator tests: none yet"
	python -m pytest fraudintel/tests -v
	@test -d dashboard/node_modules && (cd dashboard && npm test) || echo "dashboard tests: run make setup first"

test-e2e:
	python -m pytest tests/e2e -v

# Re-record the dashboard's offline replay from a real run (do this after changing the model or the simulator).
record-fallback:
	python scripts/record_fallback.py

up:
	docker compose up --build

# Needs `make up` (or the two services running). Plays the scripted scam blast and prints the headline numbers.
TELCO ?= http://localhost:8000
FRAUD ?= http://localhost:8002
demo:
	curl -s -X POST $(FRAUD)/admin/reset
	@echo
	curl -s -X POST $(TELCO)/reset
	@echo
	curl -s -X POST "$(TELCO)/scenarios/blast?wait=true" | python3 -m json.tool

# The same 500-person blast at 0.5%, 1% and 2% Linda adoption, then the average over many replays.
demo-adoption:
	@for a in 0.5 1 2; do \
	  curl -s -X POST "$(TELCO)/scenarios/blast?adoption=$$a&wait=true" | python3 scripts/print_adoption.py; \
	done
	@curl -s "$(TELCO)/adoption/odds" | python3 scripts/print_adoption.py

# ---- Production on a VPS (docs/DEPLOY.md). Needs .env.production (copy .env.production.example) ----
PROD = docker compose -f docker-compose.prod.yml --env-file .env.production

prod-up:
	$(PROD) up -d --build

prod-down:
	$(PROD) down

prod-ps:
	$(PROD) ps

prod-logs:
	$(PROD) logs -f --tail=100

# Checks the live server from outside and plays one blast, so do NOT run it during a presentation.
#   make prod-check BASE=https://linda.example.com PRESENTER_USER=presenter PRESENTER_PASS='the password'
prod-check:
	python3 scripts/smoke_prod.py "$(BASE)" "$(PRESENTER_USER)" "$(PRESENTER_PASS)"

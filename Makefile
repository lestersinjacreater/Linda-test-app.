# One-word commands (root CLAUDE.md section 7). Targets for parts not built yet say so.
.PHONY: setup data train export-model test up demo

setup:
	pip install -r ml/requirements.txt
	@test -f backend/requirements.txt && pip install -r backend/requirements.txt || echo "backend: not built yet"
	@test -f simulator/requirements.txt && pip install -r simulator/requirements.txt || echo "simulator: not built yet"
	@test -f dashboard/package.json && (cd dashboard && npm install) || echo "dashboard: not built yet"

data:
	python -I ml/src/features/dataset/synthetic.py

train: data
	python -I ml/src/features/train/train.py
	python -I ml/src/features/evaluate/evaluate.py

export-model:
	python -I ml/src/features/export/export.py

test:
	python -m pytest ml/tests -v
	@test -d backend/tests && ls backend/tests/test_*.py >/dev/null 2>&1 && python -m pytest backend/tests -v || echo "backend tests: none yet"
	@test -d simulator/tests && ls simulator/tests/test_*.py >/dev/null 2>&1 && python -m pytest simulator/tests -v || echo "simulator tests: none yet"

up:
	docker compose up --build

demo:
	@echo "demo: simulator not built yet"

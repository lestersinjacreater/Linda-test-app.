# Prompts to give Claude Code, in order (one at a time; review each plan before approving)

1. "Read CLAUDE.md and every component CLAUDE.md. Summarise the system back to me in 10 lines and list anything unclear. Do not write code yet."
2. "P0: create the repo structure from section 3, the Makefile, docker-compose.yml and one CI workflow per folder with paths filters. Move the existing simulator starter into simulator/ and the dataset pipeline into ml/ without changing their logic. Plan first."
3. "P0: write shared/test-vectors.json with 60+ cases (include real MPESA confirmations that must be SAFE) and make the Python normalizer pass them."
4. "P0: backend skeleton implementing 5.1, 5.2 and 5.4 with the confirmation rule and its tests. Replace radar_stub."
5. "P0: ml baseline train + export to model.json; wire the simulator to load it. Then run make up + make demo and show me the metrics."
6. "P0: Android reporting + consent + parity tests in CI." Then continue through P1 in section 8.

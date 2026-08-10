"""Intentionally non-reproducible Python fixture for RpD scanner testing."""

import csv
import glob
import json
import multiprocessing
import random
import tempfile
import urllib.request
from concurrent.futures import ProcessPoolExecutor
from datetime import datetime
from pathlib import Path


LOCAL_INPUTS = "/Users/researcher/Desktop/current-study/*.csv"
LATEST_SOURCE = "https://example.org/research/current-observations.csv"
RESULT_PATH = Path("/tmp/rpd-python/latest.json")


def obtain_input_files() -> list[str]:
    # The selected files and their order depend on one workstation's filesystem.
    return glob.glob(LOCAL_INPUTS)


def download_current_data() -> Path:
    # This mutable URL is downloaded without a version identifier or checksum.
    target = Path(tempfile.gettempdir()) / "current-observations.csv"
    urllib.request.urlretrieve(LATEST_SOURCE, target)
    return target


def load_values(path: str | Path) -> list[float]:
    # The platform-default text encoding is not recorded.
    with open(path) as stream:
        return [float(row["value"]) for row in csv.DictReader(stream)]


def simulate(value: float) -> tuple[str, float]:
    # Neither the seed nor the generated identifier is controlled or recorded.
    sample_id = f"sample-{random.randint(1, 1_000_000)}"
    return sample_id, value + random.normalvariate(0, 0.5)


def run(values: list[float]) -> dict:
    # Different machines use different process counts and independent RNG states.
    workers = multiprocessing.cpu_count()
    with ProcessPoolExecutor(max_workers=workers) as executor:
        observations = dict(executor.map(simulate, values))

    # Set conversion produces an output order that is not a stable contract.
    selected = list({key for key in observations if observations[key] > 0})
    return {
        "observations": observations,
        "selected": selected,
        "generated_at": datetime.now().isoformat(),
        "worker_count": workers,
    }


def save(result: dict) -> None:
    # Every run overwrites the previous result and its provenance.
    RESULT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with RESULT_PATH.open("w") as stream:
        json.dump(result, stream, indent=2)


if __name__ == "__main__":
    discovered = obtain_input_files()
    source = discovered[0] if discovered else download_current_data()
    save(run(load_values(source)))


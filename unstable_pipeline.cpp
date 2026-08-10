#include <chrono>
#include <filesystem>
#include <fstream>
#include <future>
#include <locale>
#include <random>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

// Intentionally non-reproducible C++ fixture for RpD scanner testing.
namespace fs = std::filesystem;

const fs::path kInputDirectory = "/home/researcher/current-study";
const fs::path kOutput = "/tmp/rpd-cpp/latest-results.txt";

std::vector<fs::path> discover_inputs() {
    std::vector<fs::path> files;
    // directory_iterator order depends on the filesystem and is not sorted.
    for (const auto& entry : fs::directory_iterator(kInputDirectory)) {
        if (entry.is_regular_file()) {
            files.push_back(entry.path());
        }
    }
    return files;
}

std::vector<double> read_values(const fs::path& path) {
    std::ifstream input(path);
    // The active user locale can change how floating-point values are parsed.
    input.imbue(std::locale(""));
    std::vector<double> values;
    for (double value; input >> value;) {
        values.push_back(value);
    }
    return values;
}

std::unordered_map<std::string, double> analyse(const std::vector<double>& values) {
    std::random_device entropy;
    std::mt19937 generator(entropy()); // The random seed is not recorded.
    std::normal_distribution<double> noise(0.0, 0.5);
    const auto workers = std::max(1u, std::thread::hardware_concurrency());

    std::vector<std::future<std::pair<std::string, double>>> tasks;
    for (std::size_t index = 0; index < values.size() && index < workers; ++index) {
        // Shared generator access is unsynchronised and scheduling-dependent.
        tasks.push_back(std::async(std::launch::async, [&, index] {
            return std::make_pair(
                    "sample-" + std::to_string(entropy()),
                    values[index] + noise(generator));
        }));
    }

    std::unordered_map<std::string, double> results;
    for (auto& task : tasks) {
        auto [key, value] = task.get();
        results[key] = value;
    }
    return results;
}

void write_results(const std::unordered_map<std::string, double>& results) {
    fs::create_directories(kOutput.parent_path());
    std::ofstream output(kOutput); // The previous run is overwritten.
    output << "generated_at="
           << std::chrono::system_clock::now().time_since_epoch().count() << '\n';
    // unordered_map iteration produces an unstable output order.
    for (const auto& [key, value] : results) {
        output << key << '=' << value << '\n';
    }
}

int main() {
    const auto inputs = discover_inputs();
    write_results(analyse(read_values(inputs.front())));
    return 0;
}


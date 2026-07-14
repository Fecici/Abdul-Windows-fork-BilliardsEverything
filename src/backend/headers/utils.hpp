#pragma once

#include <array>
#include <string>
#include <sstream>
#include <list>
#include <vector>
#include <cmath> 
#include <cstdint>
#include <algorithm>
#include <cstdlib>
#include <boost/cstdfloat.hpp>
#include <stdexcept>
#include <atomic>
#include <thread>

#include <unordered_map>

#include <boost/optional.hpp>
#include "classified_code_sequence.hpp"
#include "code_sequence.hpp"

inline std::atomic<bool>& cancel_flag() {
    static std::atomic<bool> f{false};
    return f;
}

inline unsigned int billiards_worker_count(unsigned int max_workers = 8) {
    const unsigned int reported = std::thread::hardware_concurrency();
    unsigned int workers = reported == 0 ? 1 : reported;

    // Release builds run on 8-16GB machines as well as developer workstations.
    // Keep native MPFR/GMP-heavy work bounded by default, but let Windows,
    // macOS, and Linux launchers override it without changing source.
    if (const char* raw = std::getenv("BILLIARDS_NATIVE_THREADS")) {
        char* end = nullptr;
        const long requested = std::strtol(raw, &end, 10);
        if (end != raw && requested > 0) {
            workers = static_cast<unsigned int>(requested);
        }
    }

    return std::max(1u, std::min(workers, std::max(1u, max_workers)));
}

inline std::size_t billiards_task_count(const std::size_t item_count, const unsigned int workers) {
    if (item_count == 0) {
        return 0;
    }

    return std::min<std::size_t>(item_count, std::max(1u, workers));
}

inline std::size_t billiards_block_size(const std::size_t item_count, const std::size_t task_count) {
    return task_count == 0 ? 0 : (item_count + task_count - 1) / task_count;
}

static std::unordered_map<std::string, CodeType> stringToCodeType = {
    {"oso", CodeType::OSO},
    {"osno", CodeType::OSNO},
    {"ons", CodeType::ONS},
    {"cs", CodeType::CS},
    {"cns", CodeType::CNS}
};

// std::unordered_set<CodeType> parse_code_type_set(const std::string& input);
std::string to_lower(const std::string& str);

std::vector<CodeType> parse_code_types(const std::string& input,
                                       const std::unordered_map<std::string, CodeType>& lookup) ;

bool is_code_type_in_list(CodeType code, const std::vector<CodeType>& allowed);

boost::optional<ClassifiedCodeSequence> convert(const std::vector<int>& codeList);

boost::optional<CodeType> getCodeType(std::vector<int32_t>& codeList);

int32_t modN(int32_t x, int32_t n);

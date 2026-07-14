#include "basic.hpp"

#include <cerrno>
#include <cstring>
#include <stdexcept>

#ifdef _WIN32
#include <direct.h>
#else
#include <sys/stat.h>
#include <sys/types.h>
#endif

namespace {

bool is_path_separator(const char c) {
    return c == '/' || c == '\\';
}

void create_directory_if_missing(const std::string& path) {
    if (path.empty()) {
        return;
    }

#ifdef _WIN32
    const int result = _mkdir(path.c_str());
#else
    const int result = mkdir(path.c_str(), 0777);
#endif

    if (result != 0 && errno != EEXIST) {
        throw std::runtime_error("Unable to create directory '" + path + "': " + std::strerror(errno));
    }
}

void create_parent_directories(const std::string& path) {
    const auto parent_end = path.find_last_of("/\\");
    if (parent_end == std::string::npos) {
        return;
    }

    const std::string parent = path.substr(0, parent_end);
    if (parent.empty()) {
        return;
    }

    // Build each parent segment explicitly so native cover writes like
    // tmp/holes.txt and cover/info.txt work from a fresh checkout or shortcut.
    for (size_t i = 0; i < parent.size(); ++i) {
        if (!is_path_separator(parent[i])) {
            continue;
        }

        // Skip Unix root "/" and Windows drive roots like "C:/".
        if (i == 0 || (i == 2 && parent.size() >= 2 && parent[1] == ':')) {
            continue;
        }

        create_directory_if_missing(parent.substr(0, i));
    }

    create_directory_if_missing(parent);
}

}

// Splitting an empty string should return an empty list
std::vector<std::string> split(const std::string& str, const std::string& delims) {

    std::vector<std::string> vec{};
    if (!str.empty()) {
        boost::split(vec, str, boost::is_any_of(delims));
    }

    return vec;
}

// Open a file with extra error handling
std::ifstream open_file_read(const std::string& path) {

    std::ifstream file{};
    // TODO what about eofbit?
    // Unfortunately, failbit causes problems when reading line-by-line
    // I really need a better way of dealing with this
    file.open(path);
    if (!file.is_open()) {
        throw std::runtime_error("Unable to open file for read: " + path);
    }

    file.exceptions(std::ifstream::badbit);

    return file;
}

// Open a file with extra error handling
std::ofstream open_file_write(const std::string& path) {

    create_parent_directories(path);

    std::ofstream file{};
    // TODO what about eofbit?
    file.open(path);
    if (!file.is_open()) {
        throw std::runtime_error("Unable to open file for write: " + path);
    }

    file.exceptions(std::ofstream::badbit | std::ofstream::failbit);

    return file;
}

std::string read_file(const std::string& path) {

    auto file = open_file_read(path);
    std::stringstream buff{};
    buff << file.rdbuf();

    return buff.str();
}

// It would be nice to have line-and-taken iterators I think

cmake_minimum_required(VERSION 3.22.1)

project("iaccept_core")

add_library(
        iaccept_core
        SHARED
        ride_engine.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        iaccept_core
        ${log-lib}
)

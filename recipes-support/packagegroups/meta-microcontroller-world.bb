SUMMARY = "All meta-microcontroller recipes"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# ngspice gets dynamically renamed
PACKAGE_ARCH = "${TUNE_PKGARCH}"

inherit packagegroup

RDEPENDS:${PN} = " \
    packagegroup-avr \
    kicad \
    ngspice \
    old-avr-projects \
    opencascade \
    vtk \
    gputils \
    sdcc \
    stcgal \
    thonny \
"

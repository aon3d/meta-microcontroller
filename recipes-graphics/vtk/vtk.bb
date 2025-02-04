SUMMARY = "The Visualization Toolkit"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://Copyright.txt;md5=074efbe58f4b7cbec2a2f6e6bdcb31e1"

inherit cmake qemu python3native pkgconfig features_check
REQUIRED_DISTRO_FEATURES = "opengl x11"

SRC_URI = " \
    https://www.vtk.org/files/release/9.0/VTK-${PV}.tar.gz \
    file://0001-Do-not-try-to-find-VTKCompileTools.patch \
    file://0002-Avoid-conflicts-struct-AllValues-with-definition-in-.patch \
    file://0003-Provide-a-fallback-FT_CALLBACK_DEF-for-freetype-2.10.patch \
    file://0004-Add-include-limits-to-fix-build-with-gcc11.patch \
"
SRC_URI[sha256sum] = "bc3eb9625b2b8dbfecb6052a2ab091fc91405de4333b0ec68f3323815154ed8a"
PV = "9.0.3"
LIBEXT = "9.0"
S = "${WORKDIR}/VTK-${PV}"

# TODO
# * meta-oe libharu -> 2.4.0
# * Qt5 support

# we use hdf5 supplied by vtk (see ThirdParty folder) because of:
#    * cmake complains for hdf5 executable missing in sysroot
#    * we enable HDF5_ENABLE_PARALLEL and add mpich to DEPENDS unconditionally
DEPENDS = " \
    qemu-native \
    coreutils-native \
    virtual/libgl \
    lz4 \
    jsoncpp \
    expat \
    tiff \
    libeigen \
    freetype \
    zlib \
    libogg \
    proj \
    pugixml \
    libxml2 \
    libpng \
    libtheora \
    glew \
    sqlite3 \
    jpeg \
    mpich \
    python3 \
"

ARCH_OECMAKE = " \
    -DH5_DISABLE_SOME_LDOUBLE_CONV_RUN__TRYRUN_OUTPUT=0 \
"
ARCH_OECMAKE:powerpc64le = " \
    -DH5_DISABLE_SOME_LDOUBLE_CONV_RUN__TRYRUN_OUTPUT=1 \
"

# stolen from meta-oe'a hdf5 and adjusted to avoid TRY_RUN
EXTRA_OECMAKE += " \
    -DVTK_PYTHON_VERSION=3 \
    -DCMAKE_REQUIRE_LARGE_FILE_SUPPORT=0 \
    -DCMAKE_REQUIRE_LARGE_FILE_SUPPORT__TRYRUN_OUTPUT=0 \
    -DTEST_LFS_WORKS_RUN=0 \
    -DTEST_LFS_WORKS_RUN__TRYRUN_OUTPUT=0 \
    -DH5_PRINTF_LL_TEST_RUN=1 \
    -DH5_PRINTF_LL_TEST_RUN__TRYRUN_OUTPUT='8' \
    -DH5_LDOUBLE_TO_LONG_SPECIAL_RUN=0 \
    -DH5_LDOUBLE_TO_LONG_SPECIAL_RUN__TRYRUN_OUTPUT= \
    -DH5_LONG_TO_LDOUBLE_SPECIAL_RUN=0 \
    -DH5_LONG_TO_LDOUBLE_SPECIAL_RUN__TRYRUN_OUTPUT= \
    -DH5_LDOUBLE_TO_LLONG_ACCURATE_RUN=0 \
    -DH5_LDOUBLE_TO_LLONG_ACCURATE_RUN__TRYRUN_OUTPUT= \
    -DH5_LLONG_TO_LDOUBLE_CORRECT_RUN=0 \
    -DH5_LLONG_TO_LDOUBLE_CORRECT_RUN__TRYRUN_OUTPUT= \
    -DH5_NO_ALIGNMENT_RESTRICTIONS_RUN=0 \
    -DH5_NO_ALIGNMENT_RESTRICTIONS_RUN__TRYRUN_OUTPUT= \
    -DH5_DISABLE_SOME_LDOUBLE_CONV_RUN=0 \
    ${ARCH_OECMAKE} \
"

EXTRA_OECMAKE += " \
    -DHDF5_ENABLE_PARALLEL=ON \
    -DVTK_USING_MPI=ON \
    -DVTK_WRAP_PYTHON=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_lz4=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_jsoncpp=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_expat=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_tiff=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_eigen=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_freetype=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_zlib=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_pegtl=OFF \
    -DVTK_MODULE_USE_EXTERNAL_VTK_lzma=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_ogg=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_libproj=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_pugixml=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_libxml2=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_png=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_theora=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_glew=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_sqlite=ON \
    -DVTK_MODULE_USE_EXTERNAL_VTK_jpeg=ON \
"

# We cannot use native vtk-compile-tools because buildhost and target can have
# different 32/64bit-ness. So adjust build.ninja that tools are run with the
# help of qemu.
def qemu_run_binary_builddir(data, rootfs_path):
    libdir = rootfs_path + data.getVar("libdir")
    base_libdir = rootfs_path + data.getVar("base_libdir")
    build_libdir = data.getVar("B") + "/lib"
    return qemu_wrapper_cmdline(data, rootfs_path, [libdir, base_libdir, build_libdir]).replace(qemu_target_binary(data), data.getVar("WORKDIR") + '/' + qemu_target_binary(data) + '-timeout')

QEMU_TIMEOUT ?= "600"

do_configure:append() {
    # create qemu wrappers:
    # * run one instance of qemu at a time (seems spawning many qemu instances
    #   in short time can lead to zombie processes)
    # * add timeout: run infinite is what makes using qemu suck
    for qemu in `find ${STAGING_BINDIR_NATIVE} -name qemu-*`; do
        qemu_name=`basename $qemu`
        if [ "x${@qemu_target_binary(d)}" = "x$qemu_name" ]; then
            wrapper_name="$qemu_name-timeout"
            echo '#!/bin/sh' > ${WORKDIR}/$wrapper_name
            echo 'set -e' >> ${WORKDIR}/$wrapper_name
            echo "flock ${WORKDIR}/qemu.lock timeout ${QEMU_TIMEOUT} $qemu_name \$@" >> ${WORKDIR}/$wrapper_name
            chmod +x ${WORKDIR}/$wrapper_name
        fi
    done

    # Adjust ${B}/build.ninja so call qemu for cross helpers
    sed -i \
        -e 's|${B}/bin/vtkH5detect|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkH5detect|g' \
        -e 's|${B}/bin/vtkH5make_libsettings|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkH5make_libsettings|g' \
        -e 's|${B}/bin/vtkParseJava-${LIBEXT}|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkParseJava-${LIBEXT}|g' \
        -e 's|${B}/bin/vtkWrapHierarchy-${LIBEXT}|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkWrapHierarchy-${LIBEXT}|g' \
        -e 's|${B}/bin/vtkWrapJava-${LIBEXT}|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkWrapJava-${LIBEXT}|g' \
        -e 's|${B}/bin/vtkWrapPython-${LIBEXT}|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkWrapPython-${LIBEXT}|g' \
        -e 's|${B}/bin/vtkWrapPythonInit-${LIBEXT}|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkWrapPythonInit-${LIBEXT}|g' \
        -e 's|VTK::WrapPythonInit|${@qemu_run_binary_builddir(d, '${STAGING_DIR_TARGET}')} ${B}/bin/vtkWrapPythonInit-${LIBEXT}|g' \
        ${B}/build.ninja
}

# Consumer's CMake checks the existence of all files referenced even if they
# are not needed for building. Make it happy by adding executables to sysroot.
# In the case a consumer really needs one of these, it has to do the same qemu
# dance as we do.
SYSROOT_DIRS:append = " ${bindir}"

PACKAGES =+ "${PN}-compile-tools ${PN}-python"
FILES:${PN}-compile-tools = "${bindir}/*${LIBEXT}"

FILES:${PN}-doc += "${datadir}/licenses/VTK"

FILES:${PN}-python = "${PYTHON_SITEPACKAGES_DIR}"
# TODO??: mpish / wxpython
RDEPENDS:${PN}-python = "python3-core python3-tkinter python3-numpy"


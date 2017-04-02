from distutils.core import setup

from os.path import join

scripts = [ join( 'bin', filename ) for filename in [ 'openyumawe' ] ]

setup(
    # Application name:
    name="OpenYumaWirelessEmulator",

    # Version number (initial):
    version="0.1.0",

    # Application author details:
    author="Alex Stancu",
    author_email="alex.stancu@radio.pub.ro",

    # Packages
    packages=["wireless_emulator"],

    # Include additional files into the package
    #include_package_data=True,

    # Details
    url="https://github.com/Melacon/OpenYuma_WE",

    #
    license="LICENSE",
    description="Wireless Transport topology emulator with OpenYuma NETCONF server, based on ONF TR-532",

    # long_description=open("README.txt").read(),

    # Dependent packages (distributions)
    #install_requires=[],
    scripts=scripts,
)
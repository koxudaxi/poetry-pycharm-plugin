# Poetry PyCharm Plugin
![CI](https://github.com/koxudaxi/poetry-pycharm-plugin/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/koxudaxi/poetry-pycharm-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/koxudaxi/poetry-pycharm-plugin)
![license](https://img.shields.io/github/license/koxudaxi/poetry-pycharm-plugin.svg)

[A JetBrains PyCharm plugin]() for [`poetry`](https://python-poetry.org/).

## This project is currently in an experimental phase

## Help
See [documentation](https://koxudaxi.github.io/poetry-pycharm-plugin/) for more details.

## Demo
![poetry_demo1](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/poetry_demo1.gif)

##  Features
### Implemented
- add/create a poetry environment as Python SDK
- create a new pyproject.toml when it does not exists
- install packages from poetry.lock
- update and lock with a popup
- show a message and a link to fix settings (QuickFix)

## TODO 
- add actions on context-menu
- add buttons to install on left-side of a editor
- publish this plugin on JetBrains' plugin market 
- CI/CD
- unittest
- document

### Complied binary
The releases section of this repository contains a compiled version of the plugin: [poetry-pycharm-plugin.zip(latest)](https://github.com/koxudaxi/poetry-pycharm-plugin/releases/latest/download/poetry-pycharm-plugin.zip)

After downloading this file, you can install the plugin from disk by following [the JetBrains instructions here](https://www.jetbrains.com/help/pycharm/plugins-settings.html).

## Motivation
[Poetry](https://github.com/python-poetry/poetry) is a popular package manager of python.

However, PyCharm doesn't support poetry.

This plugin support poetry. The code forked from the Pipenv integration code in IntelliJ-community.

In this issue [PY-30702](https://youtrack.jetbrains.com/issue/PY-30702), the feature is discussing. But, We need time to get the proper functionality in PyCharm.

The plugin has useful features like installing from poetry.lock.(you can watch demo video)

However, The feature is limited. PyCharm has to provided extension points for perfect features.

I guess if the plugin be used a lot of people, then JetBrains developers will implement extension points or poetry integration in PyCharm. 

## Screen Shots

![new_sdk](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/new_sdk.png)
![installed_package](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/installed_package.png)
![run_config](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/run_config.png)
![update_lock](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/update_lock.png)
![quick_fix](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/quick_fix.png)
![install_from_lock_file](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/install_from_lock_file.png)


## Contribute
We are waiting for your contributions to `poetry-pycharm-plugin`.


## Links


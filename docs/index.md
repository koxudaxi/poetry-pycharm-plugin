# Poetry PyCharm Plugin
![CI](https://github.com/koxudaxi/poetry-pycharm-plugin/workflows/CI/badge.svg)
[![](https://img.shields.io/jetbrains/plugin/v/14307)](https://plugins.jetbrains.com/plugin/14307-poetry)
![license](https://img.shields.io/github/license/koxudaxi/poetry-pycharm-plugin.svg)

[A JetBrains PyCharm plugin](https://plugins.jetbrains.com/plugin/14307-poetry) for [`poetry`](https://python-poetry.org/).

## This project is currently in an experimental phase

## Help
See [documentation](https://koxudaxi.github.io/poetry-pycharm-plugin/) for more details.

## Demo
![poetry_demo1](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/poetry_demo1.gif)

## Quick Installation

The plugin is in JetBrains repository ([Poetry Plugin Page](https://plugins.jetbrains.com/plugin/14307-poetry))

You can install the stable version on PyCharm's `Marketplace` (Preference -> Plugins -> Marketplace) [Official Document](https://www.jetbrains.com/help/idea/managing-plugins.html)

![search plugin](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/search_plugin.png)

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

## Feature Restrictions  
The plugin can't provide some features for technical reasons.

Because PyCharm has not provided APIs to support third-party python package managers yet. (a.k.a [extension points](https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_extensions.html)) 

We will be able to implement the following features when JetBrains add extension points to PyCharms.

- Create a new environment when creating a new project.
- Use the Custom Icon for poetry.
- Install/uninstall a package from GUI(settings)
- And more.

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


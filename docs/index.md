# Poetry PyCharm Plugin
![CI](https://github.com/koxudaxi/poetry-pycharm-plugin/workflows/CI/badge.svg)
[![](https://img.shields.io/jetbrains/plugin/v/14307)](https://plugins.jetbrains.com/plugin/14307-poetry)
![JetBrains IntelliJ Plugins](https://img.shields.io/jetbrains/plugin/r/rating/14307-poetry)
![license](https://img.shields.io/github/license/koxudaxi/poetry-pycharm-plugin.svg)

[A JetBrains PyCharm plugin](https://plugins.jetbrains.com/plugin/14307-poetry) for [`poetry`](https://python-poetry.org/).

# [This plugin has been merged into PyCharm source code🎉](https://www.jetbrains.com/pycharm/whatsnew/)
PyCharm 2021.3 or later provide poetry integration as a built-in feature.

If you have any questions then please crate an issue on [JetBrains youtrack](https://youtrack.jetbrains.com/issues)

JetBrains maintains the poetry feature in PyCharm.

## Sponsors
[![JetBrains](https://avatars.githubusercontent.com/u/60931315?s=200&v=4)](https://github.com/JetBrainsOfficial)


## Demo
![poetry_demo1](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/poetry_demo1.gif)


## Quick Installation

The plugin is in JetBrains repository ([Poetry Plugin Page](https://plugins.jetbrains.com/plugin/14307-poetry))

You can install the stable version on PyCharm's `Marketplace` (Preference -> Plugins -> Marketplace) [Official Document](https://www.jetbrains.com/help/idea/managing-plugins.html)

**I recommend PyCharm 2020.3 and the plugin version 1.0 or later to get all features and performance.**



![search plugin](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/search_plugin.png)

##  Features
### Implemented
- add a new/existing poetry environment as a interpreter 
- install packages with pyproject.toml when add a interpreter
- add a new pyproject.toml when it does not exists
- [install and import a package with poetry (QuickFix)](#screen-shots)
- install packages from poetry.lock
- update and lock with a popup
- show a message and a link to fix settings (QuickFix)
- install extras and run scripts by clicking a line marker ([Toml plugin](https://plugins.jetbrains.com/plugin/8195-toml) is required)
- show a message for outdated version packages ([Toml plugin](https://plugins.jetbrains.com/plugin/8195-toml) is required)
- install/uninstall packages from GUI
- detect poetry project when open new project

## Motivation
Poetry is a popular package manager of python.

However, PyCharm doesn't support poetry.

This plugin support poetry. This source code was forked from the Pipenv integration code in IntelliJ-community.

In this issue [PY-30702](https://youtrack.jetbrains.com/issue/PY-30702), the feature is discussing. But, We need time to get the proper functionality in PyCharm.

The plugin has useful features like installing from poetry.lock.(you can watch demo video)

However, The feature is limited. PyCharm has to provided extension points for perfect features.

I guess if the plugin be used a lot of people, then JetBrains developers will implement extension points or poetry integration in PyCharm. 


## This project is currently in an experimental phase
~~This plugin supports minimum [features](#features) to use poetry in PyCharm.~~

~~There is [feature restrictions](#feature-restrictions).~~

~~But, PyCharm team will add a few api to resolve the problem. The detail is in [this issue](https://github.com/koxudaxi/poetry-pycharm-plugin/issues/58).~~

PyCharm provides APIs that are for third-party package manager plugins.  

The project needs any feedback and PRs. We're waiting for your feedback and PRs.


## Feature Restrictions  

The plugin can't provide some features for technical reasons.

Because PyCharm has not provided APIs to support third-party python package managers yet. (a.k.a [extension points](https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_extensions.html)) 

We will be able to implement the following features when JetBrains add extension points to PyCharms.

- ~~Create a new environment when creating a new project.~~  
- Use the Custom Icon for poetry. (Coming Soon)
- ~~Install/uninstall a package from GUI(settings)~~ 
- And more.

## Screen Shots
![add_package](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/add_package.gif)
![new_sdk](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/new_sdk.png)
![installed_package](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/installed_package.png)
![run_config](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/run_config.png)
![update_lock](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/update_lock.png)
![quick_fix](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/quick_fix.png)
![install_from_lock_file](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/install_from_lock_file.png)
![install_extras](https://raw.githubusercontent.com/koxudaxi/poetry-pycharm-plugin/master/docs/extras.png)


## Contribute
We are waiting for your contributions to `poetry-pycharm-plugin`.


## Links
- [A JetBrains PyCharm plugin](https://plugins.jetbrains.com/plugin/12861-pydantic) for [`pydantic`](https://github.com/samuelcolvin/pydantic).
- I got interviewed about creating a plugin for [JetBrains' PyCharm Blog](https://blog.jetbrains.com/pycharm/2020/04/interview-koudai-aono-author-of-pydantic-plugin-for-pycharm/).


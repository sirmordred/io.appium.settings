"use strict";

const gulp = require('gulp');
const boilerplate = require('@appium/gulp-plugins').boilerplate.use(gulp);


boilerplate({
  build: 'io.appium.settings',
  files: ['index.js', '!gulpfile.js'],
});

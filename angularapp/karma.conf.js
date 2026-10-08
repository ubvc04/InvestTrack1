const fs = require('fs');
const puppeteer = require('puppeteer');

// Prefer the Chromium bundled with Puppeteer; otherwise fall back to a locally
// installed Chrome so `ng test` also works on machines without Puppeteer binaries.
function resolveChromeBin() {
  const candidates = [];
  try {
    candidates.push(puppeteer.executablePath());
  } catch (error) {
    // Puppeteer browser was not downloaded - keep going with the other candidates.
  }
  candidates.push(
    process.env.CHROME_BIN,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    '/usr/bin/chromium-browser',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium'
  );
  return candidates.find(candidate => candidate && fs.existsSync(candidate));
}

const chromeBin = resolveChromeBin();
if (chromeBin) {
  process.env.CHROME_BIN = chromeBin;
}

module.exports = function (config) {
  config.set({
    basePath: '',
    colors: false, // Disable colored output
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
      require('karma-spec-reporter'),
    ],
    client: {
      clearContext: false // Leave Jasmine Spec Runner output visible in browser
    },
    jasmineHtmlReporter: {
      suppressAll: true // Removes the duplicated traces
    },
    specReporter: {
      maxLogLines: 5, // Limit number of lines logged per test
      suppressErrorSummary: true, // Do not print error summary
      suppressFailed: false, // Do not print information about failed tests
      suppressPassed: false, // Do not print information about passed tests
      suppressSkipped: true, // Do not print information about skipped tests
      showSpecTiming: false, // Print the time elapsed per spec
      failFast: false, // Test would finish with error when a first fail occurs
      suppressColor: true, // Ensure color is suppressed in specReporter
      prefixes: {
        success: 'SUCCESS-', // Override prefix for passed test (default: '✓ ')
        failure: 'FAILED-', // Override prefix for failed test (default: '✗ ')
        skipped: 'SKIPPED-' // Override prefix for skipped test (default: '-')
      }
    },
    reporters: ['spec'],
    progressReporter: {
      showFailed: true,
      showPassed: true // Corrected the typo here
    },
    port: 9876,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ['CustomChromeHeadless'],
    customLaunchers: {
      CustomChromeHeadless: {
        base: 'ChromeHeadless',
        flags: [
          '--headless',
          '--disable-gpu',
          '--remote-debugging-port=9222',
          '--no-sandbox',
          '--disable-setuid-sandbox',
          '--disable-dev-shm-usage'
        ]
      },
    },
    singleRun: true,
    restartOnFileChange: false
  });
};

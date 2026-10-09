// Standalone validation of the generated seed dataset (run: node scripts/validate-seed-data.js)
'use strict';
const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'investments.json');
const data = JSON.parse(fs.readFileSync(file, 'utf8'));
const rows = Array.isArray(data) ? data : data.investments;

const problems = [];
const byType = {};
const byStatus = {};
const seedKeys = new Set();
const symbols = new Set();
const names = new Set();
const descriptions = new Set();
let minDate = '9999-12-31';
let maxDate = '0000-01-01';

rows.forEach((r, i) => {
  const at = `record[${i}]`;
  if (!r.seedKey) problems.push(`${at}: missing seedKey`);
  if (!r.name || r.name.length > 120) problems.push(`${at}: bad name`);
  if (!r.description || r.description.length > 4000) problems.push(`${at}: bad description`);
  if (!r.type) problems.push(`${at}: missing type`);
  if (!(typeof r.purchasePrice === 'number' && r.purchasePrice > 0)) problems.push(`${at}: bad purchasePrice`);
  if (!(typeof r.currentPrice === 'number' && r.currentPrice > 0)) problems.push(`${at}: bad currentPrice`);
  if (!(Number.isInteger(r.quantity) && r.quantity >= 1)) problems.push(`${at}: bad quantity`);
  if (typeof r.purchaseDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(r.purchaseDate)) problems.push(`${at}: bad purchaseDate`);
  if (!['Active', 'Sold'].includes(r.status)) problems.push(`${at}: status must be Active or Sold (found ${r.status})`);
  if (r.symbol && r.symbol.length > 30) problems.push(`${at}: symbol too long`);

  if (seedKeys.has(r.seedKey)) problems.push(`${at}: duplicate seedKey ${r.seedKey}`);
  seedKeys.add(r.seedKey);
  if (r.symbol) {
    if (symbols.has(r.symbol)) problems.push(`${at}: duplicate symbol ${r.symbol}`);
    symbols.add(r.symbol);
  }
  if (names.has(r.name)) problems.push(`${at}: duplicate name ${r.name}`);
  names.add(r.name);
  if (descriptions.has(r.description)) problems.push(`${at}: duplicate description`);
  descriptions.add(r.description);

  byType[r.type] = (byType[r.type] || 0) + 1;
  byStatus[r.status] = (byStatus[r.status] || 0) + 1;
  if (r.purchaseDate < minDate) minDate = r.purchaseDate;
  if (r.purchaseDate > maxDate) maxDate = r.purchaseDate;
});

console.log('file:', file);
console.log('records:', rows.length);
console.log('by type:', JSON.stringify(byType, null, 2));
console.log('by status:', JSON.stringify(byStatus, null, 2));
console.log('purchaseDate range:', minDate, '->', maxDate);
console.log('unique seedKeys:', seedKeys.size, '| unique symbols:', symbols.size, '| unique names:', names.size, '| unique descriptions:', descriptions.size);

if (rows.length < 300) problems.push(`only ${rows.length} records (need >= 300)`);

if (problems.length) {
  console.error('\nPROBLEMS:');
  problems.slice(0, 50).forEach((p) => console.error(' - ' + p));
  process.exit(1);
}
console.log('\nVALIDATION OK');

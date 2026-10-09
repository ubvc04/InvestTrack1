#!/usr/bin/env node
/*
 * Generates src/main/resources/data/investments.json
 *
 * IMPORTANT: The generated dataset is ILLUSTRATIVE DEMONSTRATION DATA.
 * Prices, quantities and valuations are fictional sample values created so the
 * application can be demonstrated end-to-end. They are NOT live market quotes,
 * verified current prices, or forecasts of any return.
 *
 * Real-world instrument names are used only where the instrument genuinely
 * exists (major stocks, ETFs, mutual funds, REITs, cryptocurrencies,
 * commodities). Everything else (most bonds, a few demo funds / real-estate
 * vehicles) is explicitly marked with "(Sample)" / "(Demo)" so it can never be
 * mistaken for a genuine, officially issued financial instrument.
 *
 * The script is deterministic: same seed => same output.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const OUT_FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'investments.json');
const TOTAL_REQUIRED = 300;

/* ------------------------------------------------------------------ */
/* Deterministic pseudo random number generator                        */
/* ------------------------------------------------------------------ */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const rand = mulberry32(0x1a2b3c4d);
const rnd = (min, max) => min + rand() * (max - min);
const rint = (min, max) => Math.floor(min + rand() * (max - min + 1));
const r2 = (v) => Math.round(v * 100) / 100;
const pick = (arr) => arr[rint(0, arr.length - 1)];

/* ------------------------------------------------------------------ */
/* Description wording pools (rotated so descriptions stay varied)      */
/* ------------------------------------------------------------------ */
const DISCLAIMER_GENERIC =
  'All prices shown are fictional sample values prepared for demonstration only; ' +
  'they are not live market quotes, verified valuations, or predictions of future returns.';
const DISCLAIMER_DEMO =
  'This is a fictional demonstration instrument created for testing only; it is not a ' +
  'genuine, officially issued security, and every price shown is a fictional sample value ' +
  'rather than a market quote.';

const OPENERS = {
  Stock: [
    'Demonstration equity holding',
    'Illustrative share position used for demo data',
    'Sample stock record for the seeded catalogue',
    'Demo portfolio line item',
    'Illustrative equity sample entry',
    'Catalogue record for search and filter testing'
  ],
  Bond: [
    'Demonstration fixed-income holding',
    'Illustrative bond position used for demo data',
    'Sample debt record for the seeded catalogue',
    'Demo fixed-income line item',
    'Illustrative bond sample entry',
    'Catalogue record for fixed-income search testing'
  ],
  Commodity: [
    'Demonstration commodity holding',
    'Illustrative raw-material position used for demo data',
    'Sample commodity record for the seeded catalogue',
    'Demo trading line item',
    'Illustrative commodity sample entry',
    'Catalogue record for commodity search testing'
  ],
  Cryptocurrency: [
    'Demonstration digital-asset holding',
    'Illustrative token position used for demo data',
    'Sample crypto record for the seeded catalogue',
    'Demo digital-asset line item',
    'Illustrative crypto sample entry',
    'Catalogue record for digital-asset search testing'
  ],
  ETF: [
    'Demonstration fund holding',
    'Illustrative exchange-traded position used for demo data',
    'Sample ETF record for the seeded catalogue',
    'Demo fund line item',
    'Illustrative exchange-traded sample entry',
    'Catalogue record for fund search testing'
  ],
  'Mutual Fund': [
    'Demonstration mutual-fund holding',
    'Illustrative pooled-investment position used for demo data',
    'Sample mutual-fund record for the seeded catalogue',
    'Demo pooled-investment line item',
    'Illustrative mutual-fund sample entry',
    'Catalogue record for mutual-fund search testing'
  ],
  'Real Estate': [
    'Demonstration real-estate holding',
    'Illustrative property-linked position used for demo data',
    'Sample real-estate record for the seeded catalogue',
    'Demo property line item',
    'Illustrative real-estate sample entry',
    'Catalogue record for real-estate search testing'
  ]
};

const CONTEXTS = {
  Stock: [
    'The holding is tracked with a static sample purchase price and a later sample valuation so list, filter and search views all return data.',
    'Quantity and price fields are fixed demonstration figures that never update, which keeps the demo environment predictable.',
    'Seeded so portfolio browsing, category filters and AI ranking have realistic equity records to work with.',
    'Position size and valuation are illustrative numbers chosen only to give the table varied, plausible-looking values.',
    'The entry supports keyword filtering across company name, ticker symbol, exchange and sector wording.',
    'Recorded as a normal active catalogue row so administrators can edit or delete it like any other investment.',
    'Purchase date and status are sample values that exercise date formatting and status filtering in the user interface.',
    'Included to give semantic search genuine sector and market vocabulary to rank against.'
  ],
  Bond: [
    'Maturity, coupon wording and valuation fields are illustrative and exist purely to populate the fixed-income category.',
    'The row exercises bond-specific filters such as maturity status and fixed-income asset class labelling.',
    'Valuation sits close to the notional sample amount, the way a held-to-maturity demo position might be displayed.',
    'Seeded so fixed-income queries, status filtering and semantic ranking have debt instruments to compare.',
    'Quantity represents a demonstration face-amount lot rather than an actual settled trade.',
    'The record gives search useful vocabulary around duration, issuer sector and maturity year.',
    'Status values distinguish live demo holdings from sample positions that have been disposed of.',
    'All amounts are fictional demonstration figures and imply no coupon, yield or credit quality.'
  ],
  Commodity: [
    'Spot-style pricing is shown as a fixed sample value so commodity filters and tables always have data.',
    'The record adds metals, energy or agricultural vocabulary for category filtering and semantic ranking.',
    'Quantity stands for a demonstration lot size rather than a contract specification of any real exchange listing.',
    'Seeded so commodity-oriented search queries return realistic-looking raw-material positions.',
    'Purchase and valuation figures are illustrative and deliberately unrelated to any current market quotation.',
    'The entry helps exercise asset-class filtering across physical and raw-material holdings.',
    'Status reflects a demo holding state and is not evidence of an actual trading position.',
    'Included to give AI search natural-resource wording such as bullion, energy crop or industrial metal.'
  ],
  Cryptocurrency: [
    'Token pricing is a fictional sample value and is deliberately not tied to any exchange rate or live quote.',
    'The entry exercises digital-asset category filtering and gives semantic search crypto-specific vocabulary.',
    'Quantity represents a demonstration wallet balance rather than an actual custody record.',
    'Seeded so crypto queries, status filters and ranking logic all have token records to work with.',
    'Valuation figures are static sample numbers that make portfolio totals look plausible in the demo UI.',
    'The record distinguishes a sample holding from an exchange listing and implies no listing status.',
    'Status values cover active demo holdings as well as sold sample positions.',
    'Included to support natural-language search over blockchain, token and digital-currency wording.'
  ],
  ETF: [
    'The fund focuses the demo portfolio on a single index or sector theme for more realistic search results.',
    'Unit price and quantity are static demonstration figures rather than end-of-day NAV or market data.',
    'Seeded so fund-category filters and semantic search have exchange-traded products to rank.',
    'The entry adds index-tracking and sector vocabulary for natural-language investment queries.',
    'Valuation is an illustrative sample amount and does not represent any authorised fund quotation.',
    'Recorded as a normal catalogue row so it can be edited, filtered and deleted like other investments.',
    'Purchase date and status are sample values that exercise date formatting and status filters.',
    'Included to give AI ranking realistic exposure to index, sector and asset-allocation wording.'
  ],
  'Mutual Fund': [
    'The fund gives the demo catalogue pooled-investment records with varied share-class style naming.',
    'Unit value and quantity are fixed demonstration figures rather than an official published NAV.',
    'Seeded so mutual-fund category filters and semantic search have pooled products to compare.',
    'The entry adds allocation and share-class vocabulary for natural-language investment queries.',
    'Valuation is an illustrative sample amount and does not represent any authorised fund quotation.',
    'Recorded as a normal catalogue row so administrators can manage it like any other investment.',
    'Purchase date and status are sample values that exercise date formatting and status filters.',
    'Included to give ranking logic genuine fund-management wording to match against.'
  ],
  'Real Estate': [
    'Property-linked records give the demo portfolio exposure to real estate vocabulary and filters.',
    'Valuation figures are static sample amounts and are not appraisals, rents or market quotations.',
    'Seeded so real-estate category filtering and semantic search have property assets to rank.',
    'The entry adds landlord, lease and property-sector wording for natural-language queries.',
    'Quantity represents a demonstration unit count, lot size or share count rather than a deed record.',
    'Recorded as a normal catalogue row that administrators can edit or remove at any time.',
    'Purchase date and status are sample values that exercise date formatting and status filters.',
    'Included so portfolio totals and asset-class breakdowns look balanced in the user interface.'
  ]
};

/* ------------------------------------------------------------------ */
/* Instrument tables                                                   */
/* ------------------------------------------------------------------ */
// [name, symbol, exchange, focus]
const STOCKS = [
  ['Apple Inc.', 'AAPL', 'NASDAQ', 'consumer electronics, smartphones and a large services ecosystem'],
  ['Microsoft Corporation', 'MSFT', 'NASDAQ', 'operating systems, productivity software and cloud infrastructure'],
  ['Alphabet Inc. Class A', 'GOOGL', 'NASDAQ', 'internet search, digital advertising and cloud services'],
  ['Amazon.com Inc.', 'AMZN', 'NASDAQ', 'e-commerce marketplaces, cloud infrastructure and digital content'],
  ['NVIDIA Corporation', 'NVDA', 'NASDAQ', 'graphics processors and accelerators used for AI workloads and gaming'],
  ['Meta Platforms Inc.', 'META', 'NASDAQ', 'social networking, digital advertising and virtual reality products'],
  ['Tesla Inc.', 'TSLA', 'NASDAQ', 'electric vehicles, battery storage and solar energy products'],
  ['Broadcom Inc.', 'AVGO', 'NASDAQ', 'semiconductors and infrastructure software for networking'],
  ['JPMorgan Chase & Co.', 'JPM', 'NYSE', 'universal bank spanning consumer banking, markets and asset management'],
  ['Visa Inc.', 'V', 'NYSE', 'payment network that processes card transactions worldwide'],
  ['Mastercard Incorporated', 'MA', 'NYSE', 'global payments technology and card network services'],
  ['Johnson & Johnson', 'JNJ', 'NYSE', 'pharmaceuticals, medical devices and consumer health products'],
  ['Walmart Inc.', 'WMT', 'NYSE', 'discount retail and grocery supercenters across several countries'],
  ['The Procter & Gamble Company', 'PG', 'NYSE', 'household cleaning and personal care consumer brands'],
  ['The Coca-Cola Company', 'KO', 'NYSE', 'non-alcoholic beverages and concentrate production'],
  ['PepsiCo Inc.', 'PEP', 'NYSE', 'savory snacks and beverages sold through a global distribution network'],
  ['The Home Depot Inc.', 'HD', 'NYSE', 'home improvement retail stores serving DIY and professional customers'],
  ['The Walt Disney Company', 'DIS', 'NYSE', 'media networks, streaming entertainment and theme parks'],
  ['Netflix Inc.', 'NFLX', 'NASDAQ', 'subscription streaming of films and television series'],
  ['Adobe Inc.', 'ADBE', 'NASDAQ', 'creative design, document and marketing software applications'],
  ['Salesforce Inc.', 'CRM', 'NYSE', 'customer relationship management cloud software'],
  ['Oracle Corporation', 'ORCL', 'NYSE', 'enterprise databases, applications and cloud infrastructure'],
  ['Intel Corporation', 'INTC', 'NASDAQ', 'microprocessors, chipsets and semiconductor manufacturing'],
  ['Cisco Systems Inc.', 'CSCO', 'NASDAQ', 'networking hardware, collaboration tools and cybersecurity software'],
  ['Pfizer Inc.', 'PFE', 'NYSE', 'prescription medicines, vaccines and hospital products'],
  ['Merck & Co. Inc.', 'MRK', 'NYSE', 'pharmaceuticals, vaccines and animal health treatments'],
  ['AbbVie Inc.', 'ABBV', 'NYSE', 'biopharmaceuticals focused on immunology and oncology'],
  ['Abbott Laboratories', 'ABT', 'NYSE', 'medical devices, diagnostics and nutritional products'],
  ['Thermo Fisher Scientific Inc.', 'TMO', 'NYSE', 'laboratory instruments, reagents and life sciences services'],
  ['Costco Wholesale Corporation', 'COST', 'NASDAQ', 'membership warehouse clubs selling bulk merchandise'],
  ['Exxon Mobil Corporation', 'XOM', 'NYSE', 'integrated oil and gas exploration, refining and chemicals'],
  ['Chevron Corporation', 'CVX', 'NYSE', 'upstream energy production, refining and petrochemicals'],
  ['Caterpillar Inc.', 'CAT', 'NYSE', 'construction, mining and forestry equipment manufacturing'],
  ['The Boeing Company', 'BA', 'NYSE', 'commercial aircraft manufacturing and defense systems'],
  ['The Goldman Sachs Group Inc.', 'GS', 'NYSE', 'investment banking, trading and wealth management'],
  ['Morgan Stanley', 'MS', 'NYSE', 'investment banking, securities underwriting and wealth management'],
  ['UnitedHealth Group Incorporated', 'UNH', 'NYSE', 'health benefits plans and healthcare analytics services'],
  ['NIKE Inc.', 'NKE', 'NYSE', 'athletic footwear, apparel and equipment brands'],
  ['Starbucks Corporation', 'SBUX', 'NASDAQ', 'company-operated and licensed coffeehouse retail stores'],
  ["McDonald's Corporation", 'MCD', 'NYSE', 'quick service restaurant franchise system'],
  ['AT&T Inc.', 'T', 'NYSE', 'wireless communications, broadband and pay television'],
  ['Verizon Communications Inc.', 'VZ', 'NYSE', 'wireless networks, fibre broadband and media services'],
  ['International Business Machines Corporation', 'IBM', 'NYSE', 'enterprise IT services, middleware and mainframe systems'],
  ['Advanced Micro Devices Inc.', 'AMD', 'NASDAQ', 'processors and graphics chips for PCs, consoles and data centres'],
  ['QUALCOMM Incorporated', 'QCOM', 'NASDAQ', 'wireless chipsets and mobile connectivity technologies'],
  ['Texas Instruments Incorporated', 'TXN', 'NASDAQ', 'analog and embedded semiconductors for industrial electronics'],
  ['Intuitive Surgical Inc.', 'ISRG', 'NASDAQ', 'robotic-assisted surgical systems and instrument platforms'],
  ['Stryker Corporation', 'SYK', 'NYSE', 'orthopaedic implants, surgical equipment and medical technology'],
  ['Gilead Sciences Inc.', 'GILD', 'NASDAQ', 'antiviral and oncology prescription therapies'],
  ['Amgen Inc.', 'AMGN', 'NASDAQ', 'biotech medicines for oncology, inflammation and rare diseases'],
  ['Eli Lilly and Company', 'LLY', 'NYSE', 'pharmaceuticals including diabetes, obesity and neuroscience care'],
  ['Union Pacific Corporation', 'UNP', 'NYSE', 'freight railroad network across the western United States'],
  ['United Parcel Service Inc.', 'UPS', 'NYSE', 'global package delivery, freight and logistics services'],
  ['General Electric Company', 'GE', 'NYSE', 'jet engines, power generation equipment and aviation services'],
  ['Honeywell International Inc.', 'HON', 'NASDAQ', 'industrial automation, aerospace controls and building technologies'],
  ['Linde plc', 'LIN', 'NASDAQ', 'industrial and specialty gases for healthcare and manufacturing'],
  ['NextEra Energy Inc.', 'NEE', 'NYSE', 'regulated electric utility and large-scale renewable generation'],
  ['Berkshire Hathaway Inc. Class B', 'BRK.B', 'NYSE', 'diversified holding company with insurance and operating businesses'],
  ['American Express Company', 'AXP', 'NYSE', 'charge cards, credit cards and expense management services'],
  ['BlackRock Inc.', 'BLK', 'NYSE', 'asset management, index funds and investment risk technology'],
  ['Citigroup Inc.', 'C', 'NYSE', 'global banking, markets and treasury services'],
  ['Bank of America Corporation', 'BAC', 'NYSE', 'retail banking, lending, cards and wealth management'],
  ['Wells Fargo & Company', 'WFC', 'NYSE', 'consumer banking, mortgages and commercial lending'],
  ['Accenture plc', 'ACN', 'NYSE', 'management consulting, technology and outsourcing services'],
  ['Comcast Corporation', 'CMCSA', 'NASDAQ', 'cable television, broadband internet and studio content'],
  ['The TJX Companies Inc.', 'TJX', 'NYSE', 'off-price apparel and home fashion retail chains'],
  ['Analog Devices Inc.', 'ADI', 'NASDAQ', 'signal processing and data conversion semiconductors'],
  ['Palo Alto Networks Inc.', 'PANW', 'NASDAQ', 'network security platforms and cloud-delivered firewalls'],
  ['Booking Holdings Inc.', 'BKNG', 'NASDAQ', 'online travel reservations across hotels, flights and rentals'],
  ['Cintas Corporation', 'CTAS', 'NASDAQ', 'workplace uniforms, facility services and document management']
];

// [name, symbol, exchange, focus]
const ETFS = [
  ['Vanguard S&P 500 ETF', 'VOO', 'NYSE Arca', 'tracks the S&P 500 large-cap US equity index'],
  ['iShares Core S&P 500 ETF', 'IVV', 'NYSE Arca', 'tracks the S&P 500 index with a broad large-cap basket'],
  ['SPDR S&P 500 ETF Trust', 'SPY', 'NYSE Arca', 'tracks the S&P 500 index as one of the oldest index trusts'],
  ['Vanguard Total Stock Market ETF', 'VTI', 'NYSE Arca', 'covers the entire US equity market across size segments'],
  ['iShares Core Total US Stock Market ETF', 'ITOT', 'NYSE Arca', 'tracks the whole US stock market in a single low-cost fund'],
  ['Schwab U.S. Broad Market ETF', 'SCHB', 'NYSE Arca', 'holds a broad basket of large, mid and small-cap US shares'],
  ['Invesco QQQ Trust', 'QQQ', 'NASDAQ', 'tracks the Nasdaq-100 index of large non-financial companies'],
  ['Invesco NASDAQ 100 ETF', 'QQQM', 'NASDAQ', 'tracks the Nasdaq-100 index in a share-class structure'],
  ['Vanguard Growth ETF', 'VUG', 'NYSE Arca', 'holds large-cap US companies with above-average growth characteristics'],
  ['Vanguard Value ETF', 'VTV', 'NYSE Arca', 'holds large-cap US companies trading at lower valuation ratios'],
  ['Vanguard Mid-Cap ETF', 'VO', 'NYSE Arca', 'tracks mid-capitalisation US equities'],
  ['Vanguard Small-Cap ETF', 'VB', 'NYSE Arca', 'tracks small-capitalisation US equities'],
  ['Vanguard FTSE Developed Markets ETF', 'VEA', 'NYSE Arca', 'holds developed-market equities outside the United States'],
  ['Vanguard FTSE Emerging Markets ETF', 'VWO', 'NYSE Arca', 'holds equities from emerging market economies'],
  ['iShares Core MSCI EAFE ETF', 'IEFA', 'NYSE Arca', 'tracks developed-market shares in Europe, Australasia and the Far East'],
  ['iShares Core MSCI Emerging Markets ETF', 'IEMG', 'NYSE Arca', 'tracks large and mid-cap emerging market equities'],
  ['iShares Core MSCI Total International Stock ETF', 'IXUS', 'NASDAQ', 'combines developed and emerging market equities outside the US'],
  ['Vanguard Total International Stock ETF', 'VXUS', 'NASDAQ', 'holds the full international equity market excluding the US'],
  ['iShares Core U.S. Aggregate Bond ETF', 'AGG', 'NYSE Arca', 'tracks the broad US investment-grade bond market'],
  ['Vanguard Total Bond Market ETF', 'BND', 'NYSE Arca', 'holds US government, corporate and securitised investment-grade bonds'],
  ['iShares 7-10 Year Treasury Bond ETF', 'IEF', 'NYSE Arca', 'tracks intermediate-term US Treasury notes'],
  ['iShares 20+ Year Treasury Bond ETF', 'TLT', 'NASDAQ', 'tracks long-dated US Treasury bonds with higher duration'],
  ['iShares iBoxx $ Investment Grade Corporate Bond ETF', 'LQD', 'NYSE Arca', 'holds investment-grade US dollar corporate bonds'],
  ['iShares iBoxx $ High Yield Corporate Bond ETF', 'HYG', 'NYSE Arca', 'holds lower-rated corporate bonds commonly called high yield'],
  ['Vanguard Real Estate ETF', 'VNQ', 'NYSE Arca', 'tracks US real estate investment trusts across property types'],
  ['iShares U.S. Real Estate ETF', 'IYR', 'NYSE Arca', 'holds US real estate companies and property trusts'],
  ['Schwab U.S. REIT ETF', 'SCHH', 'NYSE Arca', 'tracks US real estate investment trusts with a small expense ratio'],
  ['SPDR Gold Shares', 'GLD', 'NYSE Arca', 'backs each share with allocated gold bullion holdings'],
  ['iShares Silver Trust', 'SLV', 'NYSE Arca', 'backs each share with physical silver bullion'],
  ['abrdn Physical Gold Shares ETF', 'SGOL', 'NYSE Arca', 'holds physically allocated gold in secure vaults'],
  ['United States Oil Fund LP', 'USO', 'NYSE Arca', 'tracks front-month WTI crude oil futures prices'],
  ['United States Brent Oil Fund LP', 'BNO', 'NYSE Arca', 'tracks front-month Brent crude oil futures prices'],
  ['Invesco DB Commodity Index Tracking Fund', 'DBC', 'NYSE Arca', 'tracks a diversified index of energy, metals and agricultural futures'],
  ['iShares S&P GSCI Commodity-Indexed Trust', 'GSG', 'NYSE Arca', 'tracks a broad basket of commodity futures contracts'],
  ['Financial Select Sector SPDR Fund', 'XLF', 'NYSE Arca', 'holds large US banks, insurers and asset managers'],
  ['Technology Select Sector SPDR Fund', 'XLK', 'NYSE Arca', 'holds large US technology and IT services companies'],
  ['Health Care Select Sector SPDR Fund', 'XLV', 'NYSE Arca', 'holds large US healthcare, pharma and device companies'],
  ['Energy Select Sector SPDR Fund', 'XLE', 'NYSE Arca', 'holds large US oil, gas and energy equipment companies'],
  ['Industrial Select Sector SPDR Fund', 'XLI', 'NYSE Arca', 'holds large US industrials, transport and machinery companies'],
  ['Consumer Staples Select Sector SPDR Fund', 'XLP', 'NYSE Arca', 'holds large US food, household and personal care companies'],
  ['Utilities Select Sector SPDR Fund', 'XLU', 'NYSE Arca', 'holds large US electric, gas and water utilities'],
  ['Materials Select Sector SPDR Fund', 'XLB', 'NYSE Arca', 'holds large US chemicals, metals and mining companies'],
  ['Communication Services Select Sector SPDR Fund', 'XLC', 'NYSE Arca', 'holds large US media, telecom and interactive media companies'],
  ['Real Estate Select Sector SPDR Fund', 'XLRE', 'NYSE Arca', 'holds US real estate companies selected from the S&P 500'],
  ['Schwab U.S. Dividend Equity ETF', 'SCHD', 'NYSE Arca', 'holds US companies with a record of consistent dividend payments'],
  ['Vanguard Dividend Appreciation ETF', 'VIG', 'NYSE Arca', 'holds US companies that have raised dividends over time'],
  ['iShares Core S&P Mid-Cap ETF', 'IJH', 'NYSE Arca', 'tracks mid-capitalisation companies in the S&P MidCap 400'],
  ['Vanguard Small-Cap Value ETF', 'VBR', 'NYSE Arca', 'holds small-cap US companies with lower valuation ratios'],
  ['iShares MSCI Japan ETF', 'EWJ', 'NYSE Arca', 'tracks large and mid-cap Japanese equities'],
  ['Vanguard Information Technology ETF', 'VGT', 'NYSE Arca', 'holds US companies across the information technology sector'],
  ['VanEck Gold Miners ETF', 'GDX', 'NYSE Arca', 'holds companies engaged in gold and silver mining'],
  ['iShares U.S. Home Construction ETF', 'ITB', 'NYSE Arca', 'holds US homebuilders and building products companies'],
  ['SPDR S&P Dividend ETF', 'SDIV', 'NYSE Arca', 'holds US companies ranked by high dividend yields'],
  ['Schwab International Equity ETF', 'SCHF', 'NYSE Arca', 'tracks developed-market equities outside the United States']
];

// Real mutual funds: [name, symbol, exchange, focus]
const MUTUAL_FUNDS_REAL = [
  ['Vanguard Total Stock Market Index Fund Admiral Shares', 'VTSAX', null, 'holds virtually the entire US equity market in one index portfolio'],
  ['Vanguard 500 Index Fund Admiral Shares', 'VFIAX', null, 'tracks the S&P 500 index with a broad large-cap basket'],
  ['Vanguard Total Bond Market Index Fund Admiral Shares', 'VBTLX', null, 'holds the full US investment-grade bond market'],
  ['Vanguard Total International Stock Index Fund Admiral Shares', 'VTIAX', null, 'holds international equities across developed and emerging markets'],
  ['Vanguard Balanced Index Fund Admiral Shares', 'VBIAX', null, 'combines US stocks and investment-grade bonds in a static allocation'],
  ['Vanguard Growth Index Fund Admiral Shares', 'VIGAX', null, 'tracks large-cap US growth companies'],
  ['Vanguard Value Index Fund Admiral Shares', 'VVIAX', null, 'tracks large-cap US value companies'],
  ['Vanguard Mid-Cap Index Fund Admiral Shares', 'VIMAX', null, 'tracks the CRSP US Mid Cap index segment'],
  ['Vanguard Small-Cap Index Fund Admiral Shares', 'VSMAX', null, 'tracks the CRSP US Small Cap index segment'],
  ['Vanguard Real Estate Index Fund Admiral Shares', 'VGSLX', null, 'holds US real estate investment trusts across property sectors'],
  ['Vanguard Dividend Appreciation Index Fund Admiral Shares', 'VDAIX', null, 'holds US companies with a record of raising dividends'],
  ['Vanguard Wellesley Income Fund Investor Shares', 'VWINX', null, 'combines income-oriented bonds with a conservative stock sleeve'],
  ['Vanguard Wellington Fund Investor Shares', 'VWELX', null, 'blends a balanced portfolio of stocks and taxable bonds'],
  ['Fidelity 500 Index Fund', 'FXAIX', null, 'tracks the S&P 500 index'],
  ['Fidelity Total Market Index Fund', 'FSKAX', null, 'holds the full US stock market across capitalisation ranges'],
  ['Fidelity Total International Index Fund', 'FITFX', null, 'holds developed and emerging market equities outside the US'],
  ['Fidelity Contrafund Fund', 'FCNTX', null, 'invests in companies expected to sustain above-average earnings growth'],
  ['Fidelity Blue Chip Growth Fund', 'FBGRX', null, 'invests in large, well-known companies with growth potential'],
  ['Fidelity Balanced Fund', 'FBALX', null, 'maintains a mixed allocation of stocks and bonds'],
  ['Fidelity Investment Grade Bond Fund', 'FBNDX', null, 'holds investment-grade US dollar debt securities'],
  ['Fidelity ZERO Total Market Index Fund', 'FZROX', null, 'tracks the total US stock market with no fund fee'],
  ['Schwab Total Stock Market Index Fund', 'SWTSX', null, 'tracks the broad US equity market'],
  ['Schwab S&P 500 Index Fund', 'SWPPX', null, 'tracks the S&P 500 index'],
  ['Schwab International Index Fund', 'SWISX', null, 'tracks developed-market equities outside the US'],
  ['Schwab U.S. Aggregate Bond Index Fund', 'SWAGX', null, 'tracks the broad US investment-grade bond market'],
  ['Schwab Dividend Equity Fund', 'SWDSX', null, 'invests in US companies with consistent dividend payments'],
  ['American Funds Growth Fund of America Class A', 'AGTHX', null, 'invests in growing companies across global markets'],
  ['American Funds Investment Company of America Class A', 'AIVSX', null, 'a balanced fund holding established US companies'],
  ['T. Rowe Price Blue Chip Growth Fund', 'TRBCX', null, 'invests in large established companies with growth prospects'],
  ['Dodge & Cox Stock Fund', 'DODGX', null, 'invests in US companies trading below estimated intrinsic value'],
  ['PIMCO Total Return Fund Investor Class', 'PTTRX', null, 'actively manages a broad portfolio of global fixed-income securities'],
  ['DoubleLine Total Return Bond Fund', 'DBLTX', null, 'actively manages taxable US dollar bond positions'],
  ['Fidelity Magellan Fund', 'FMAGX', null, 'invests primarily in established US companies with growth potential']
];

// Clearly fictional demo mutual funds: [name, symbol, exchange, focus]
const MUTUAL_FUNDS_DEMO = [
  ['Sample Growth Allocation Fund (Demo)', 'DEMO-MF-01', null, 'a fictional blended growth portfolio created for demo search data'],
  ['Sample Balanced Income Fund (Demo)', 'DEMO-MF-02', null, 'a fictional mix of income securities and defensive shares'],
  ['Sample Global Equity Fund (Demo)', 'DEMO-MF-03', null, 'a fictional worldwide equity basket spanning several regions'],
  ['Sample Short-Term Bond Fund (Demo)', 'DEMO-MF-04', null, 'a fictional portfolio of short-maturity debt instruments'],
  ['Sample Emerging Markets Fund (Demo)', 'DEMO-MF-05', null, 'a fictional allocation to developing-market securities'],
  ['Sample Retirement 2045 Fund (Demo)', 'DEMO-MF-06', null, 'a fictional target-date portfolio that de-risks over time'],
  ['Sample Sustainable Industries Fund (Demo)', 'DEMO-MF-07', null, 'a fictional portfolio focused on environmentally oriented businesses']
];

// Real REITs: [name, symbol, exchange, focus]
const REITS_REAL = [
  ['Prologis Inc.', 'PLD', 'NYSE', 'a landlord for large logistics warehouses and distribution centres'],
  ['American Tower Corporation', 'AMT', 'NYSE', 'owner and operator of wireless communications towers'],
  ['Equinix Inc.', 'EQIX', 'NASDAQ', 'operator of carrier-neutral data centres worldwide'],
  ['Digital Realty Trust Inc.', 'DLR', 'NYSE', 'owner of data centre and colocation properties'],
  ['Realty Income Corporation', 'O', 'NYSE', 'net-lease retail property owner paying monthly distributions'],
  ['Welltower Inc.', 'WELL', 'NYSE', 'owner of senior housing, post-acute and outpatient medical properties'],
  ['Public Storage', 'PSA', 'NYSE', 'self-storage facilities across the United States and Europe'],
  ['Simon Property Group Inc.', 'SPG', 'NYSE', 'operator of premium outlet malls and shopping centres'],
  ['VICI Properties Inc.', 'VICI', 'NYSE', 'owner of casino, hospitality and entertainment real estate'],
  ['Crown Castle Inc.', 'CCI', 'NYSE', 'owner of towers and fibre infrastructure for mobile networks'],
  ['Alexandria Real Estate Equities Inc.', 'ARE', 'NYSE', 'owner of life science campuses clustered in innovation markets'],
  ['Equity Residential', 'EQR', 'NYSE', 'apartment property owner focused on coastal urban markets'],
  ['AvalonBay Communities Inc.', 'AVB', 'NYSE', 'developer and owner of high-quality apartment communities'],
  ['Mid-America Apartment Communities Inc.', 'MAA', 'NYSE', 'apartment owner operating across the southeastern United States'],
  ['Iron Mountain Inc.', 'IRM', 'NYSE', 'secure document storage, records management and data centres'],
  ['Ventas Inc.', 'VTR', 'NYSE', 'owner of senior housing and healthcare-related properties'],
  ['UDR Inc.', 'UDR', 'NYSE', 'apartment REIT with communities in multiple US markets'],
  ['Host Hotels & Resorts Inc.', 'HST', 'NYSE', 'owner of luxury and upper-upscale hotel properties'],
  ['Extra Space Storage Inc.', 'EXR', 'NYSE', 'self-storage facilities and storage management services'],
  ['NNN REIT Inc.', 'NNN', 'NYSE', 'long-term net lease retail properties across the United States']
];

// Clearly fictional demo real-estate vehicles: [name, symbol, exchange, focus]
const REAL_ESTATE_DEMO = [
  ['Sample Residential Rental Fund (Demo)', 'DEMO-RE-01', null, 'a fictional basket of rental apartments created for demo data'],
  ['Sample Commercial Property Basket (Demo)', 'DEMO-RE-02', null, 'a fictional collection of leased office and retail units'],
  ['Sample Logistics Warehouse Trust (Demo)', 'DEMO-RE-03', null, 'a fictional group of distribution and warehouse facilities'],
  ['Illustrative Real Estate Crowdfunding Pool (Sample)', 'DEMO-RE-04', null, 'a fictional pooled property investment created for demonstration'],
  ['Sample Student Housing Fund (Demo)', 'DEMO-RE-05', null, 'a fictional portfolio of properties near university campuses'],
  ['Sample Healthcare Property Fund (Demo)', 'DEMO-RE-06', null, 'a fictional set of clinics, care homes and medical offices'],
  ['Sample Data Centre Development Fund (Demo)', 'DEMO-RE-07', null, 'a fictional vehicle funding data centre construction projects'],
  ['Sample Mixed-Use Development Vehicle (Demo)', 'DEMO-RE-08', null, 'a fictional project combining residential, retail and office space'],
  ['Sample Solar Land Lease Portfolio (Demo)', 'DEMO-RE-09', null, 'a fictional portfolio of land leased for solar generation'],
  ['Sample Holiday Resort Property Fund (Demo)', 'DEMO-RE-10', null, 'a fictional collection of leisure and resort properties'],
  ['Sample Suburban Office Park Vehicle (Demo)', 'DEMO-RE-11', null, 'a fictional group of low-rise suburban office buildings'],
  ['Sample Affordable Housing Partnership (Demo)', 'DEMO-RE-12', null, 'a fictional partnership owning subsidised housing units'],
  ['Sample Retail Plaza Income Fund (Demo)', 'DEMO-RE-13', null, 'a fictional set of neighbourhood retail plazas'],
  ['Sample Industrial Land Bank Vehicle (Demo)', 'DEMO-RE-14', null, 'a fictional holding of land zoned for industrial development'],
  ['Sample Branded Residence Portfolio (Demo)', 'DEMO-RE-15', null, 'a fictional portfolio of hotel-branded residential apartments']
];

// Commodities: [name, symbol, focus]
const COMMODITIES = [
  ['Gold', 'XAU', 'precious bullion typically held as a store of value and hedge asset'],
  ['Silver', 'XAG', 'precious metal used both as an investment and in industrial fabrication'],
  ['Platinum', 'XPT', 'scarce platinum-group metal used in jewellery and catalytic converters'],
  ['Palladium', 'XPD', 'platinum-group metal used mainly in automotive emission control'],
  ['Copper', 'XCU', 'industrial base metal closely tied to construction and electrification demand'],
  ['Aluminium', 'ALU', 'lightweight industrial metal used in transport, packaging and construction'],
  ['Zinc', 'ZNC', 'base metal used mainly for galvanising steel against corrosion'],
  ['Nickel', 'NIC', 'base metal used in stainless steel production and battery alloys'],
  ['Tin', 'TIN', 'soft metal used in soldering electronics and tin plating'],
  ['Lithium Carbonate', 'LTH', 'battery-grade chemical feedstock used in lithium-ion cells'],
  ['Crude Oil WTI', 'WTI', 'light sweet crude benchmark priced in US dollars per barrel'],
  ['Brent Crude Oil', 'BRN', 'sea-borne crude benchmark used across international oil markets'],
  ['Natural Gas', 'NGAS', 'burnable gas benchmark used for power generation and heating'],
  ['RBOB Gasoline', 'RBOT', 'refined motor gasoline benchmark ahead of driving seasons'],
  ['Heating Oil', 'HOIL', 'distilled fuel used for heating buildings and industrial purposes'],
  ['Uranium', 'U3O8', 'nuclear fuel concentrate used by power generation utilities'],
  ['Corn', 'CORN', 'staple agricultural grain used for food, feed and fuel'],
  ['Wheat', 'WHT', 'cereal grain used for flour, milling and food production'],
  ['Soybeans', 'SOY', 'oilseed crop used for animal feed and vegetable oil'],
  ['Rice', 'RICE', 'staple food grain consumed across much of the world'],
  ['Coffee Arabica', 'COF', 'washed arabica coffee beans traded against weather risk'],
  ['Raw Sugar', 'SUGR', 'bulk sweetener crop traded in physical commodity markets'],
  ['Cotton', 'COTN', 'soft fibre crop used by the textile industry'],
  ['Cocoa Beans', 'COCO', 'chocolate ingredient grown mainly in West Africa'],
  ['Orange Juice', 'OJU', 'frozen concentrated juice contract sensitive to citrus harvests'],
  ['Canola', 'CANL', 'oilseed crushed for vegetable oil and animal feed'],
  ['Live Cattle', 'CATTLE', 'fed cattle contract linked to beef processing demand'],
  ['Feeder Cattle', 'FEEDH', 'younger cattle contract tied to calf and grazing markets'],
  ['Lean Hogs', 'HOGS', 'pork belly substitute contract used by meat processors'],
  ['Lumber', 'LUMB', 'sawn softwood contract sensitive to housing construction activity'],
  ['Carbon Emission Allowances', 'CARB', 'tradeable permits covering regulated greenhouse gas emissions'],
  ['Class III Milk', 'MILK', 'milk used mainly for cheese and dairy manufacturing'],
  ['Natural Rubber', 'RUBR', 'industrial crop used in tyres and manufactured rubber goods'],
  ['Iron Ore', 'IRON', 'steel-making raw material shipped in bulk from major miners'],
  ['Palm Oil', 'PALM', 'edible vegetable oil used in food and chemical products']
];

// Cryptocurrencies: [name, symbol, focus]
const CRYPTOS = [
  ['Bitcoin', 'BTC', 'the original decentralised cryptocurrency secured by proof-of-work mining'],
  ['Ethereum', 'ETH', 'programmable blockchain asset used for smart contracts and applications'],
  ['XRP', 'XRP', 'digital asset designed for fast cross-border settlement'],
  ['Cardano', 'ADA', 'proof-of-stake blockchain asset with a research-led design process'],
  ['Solana', 'SOL', 'high-throughput blockchain asset used for decentralised applications'],
  ['Dogecoin', 'DOGE', 'branded meme cryptocurrency with an active community'],
  ['Polkadot', 'DOT', 'interoperability token connecting specialised blockchain networks'],
  ['Chainlink', 'LINK', 'oracle token that supplies external data to smart contracts'],
  ['Litecoin', 'LTC', 'early bitcoin-inspired cryptocurrency with faster block times'],
  ['Avalanche', 'AVAX', 'layer-one blockchain asset used for decentralised finance applications'],
  ['TRON', 'TRX', 'blockchain asset focused on content sharing and stablecoin transfers'],
  ['Shiba Inu', 'SHIB', 'ethereum-based token with a large retail following'],
  ['Stellar', 'XLM', 'payment-focused network token aimed at low-cost transfers'],
  ['Monero', 'XMR', 'privacy-oriented cryptocurrency with enhanced transaction confidentiality'],
  ['Ethereum Classic', 'ETC', 'original ethereum ledger token preserved after the 2016 fork'],
  ['Cosmos', 'ATOM', 'interop token securing a network of connected application chains'],
  ['Aptos', 'APT', 'layer-one blockchain asset built for scalable general-purpose applications'],
  ['Arbitrum', 'ARB', 'governance token of an ethereum layer-two scaling network'],
  ['Optimism', 'OP', 'governance token of an optimistic rollup scaling network'],
  ['Uniswap', 'UNI', 'governance token of a major decentralised exchange protocol'],
  ['Aave', 'AAVE', 'governance token of a decentralised lending and borrowing protocol'],
  ['Filecoin', 'FIL', 'storage token rewarding providers of decentralised file capacity'],
  ['Hedera', 'HBAR', 'network token of an enterprise distributed ledger governed by a council'],
  ['VeChain', 'VET', 'supply-chain token used to record product provenance data'],
  ['Algorand', 'ALGO', 'proof-of-stake blockchain asset aimed at payments and finance'],
  ['Tezos', 'XTZ', 'self-upgrading proof-of-stake blockchain asset'],
  ['EOS', 'EOS', 'blockchain asset supporting decentralised application deployment'],
  ['Neo', 'NEO', 'blockchain asset often described as a smart economy platform'],
  ['Internet Computer', 'ICP', 'token for a network hosting applications on decentralised infrastructure'],
  ['Near Protocol', 'NEAR', 'layer-one blockchain asset designed for usability and sharding'],
  ['Stacks', 'STX', 'token enabling smart contracts secured by the bitcoin network'],
  ['Immutable', 'IMX', 'token of a layer-two network focused on digital collectible trading'],
  ['Maker', 'MKR', 'governance token of a decentralised stablecoin protocol'],
  ['Injective', 'INJ', 'token of a finance-focused interoperable blockchain'],
  ['Sui', 'SUI', 'layer-one blockchain asset built around parallel transaction execution']
];

/* ------------------------------------------------------------------ */
/* Bond construction (all clearly marked fictional / sample)           */
/* ------------------------------------------------------------------ */
const BOND_ISSUERS = [
  ['Cedarline Utilities', 'utility company serving regulated electricity and water networks'],
  ['Northvane Manufacturing', 'industrial manufacturer of machinery and fabricated components'],
  ['Brightpath Retail Group', 'multi-format retailer operating supermarkets and convenience stores'],
  ['Harborstone Foods', 'food producer supplying packaged groceries and chilled products'],
  ['Vantage Ridge Logistics', 'freight operator running warehousing and long-haul distribution'],
  ['Silverbrook Healthcare', 'healthcare group operating clinics and diagnostic laboratories'],
  ['Ironhollow Steelworks', 'steel producer supplying construction and automotive customers'],
  ['Clearwater Media', 'media company owning broadcast channels and digital publishing assets'],
  ['Redstone Chemicals', 'specialty chemical manufacturer serving industrial customers'],
  ['Lakeshore Apparel', 'apparel company designing and distributing branded clothing lines'],
  ['Summit Peak Airlines', 'regional airline operating passenger and cargo routes'],
  ['Everfield AgriCo', 'agricultural business trading grains, seeds and fertiliser inputs'],
  ['Granite Coast Shipping', 'container shipping line moving goods across ocean trade routes'],
  ['Blue Cedar Telecom', 'telecommunications carrier providing fibre and mobile connectivity'],
  ['Pioneer Grid Energy', 'independent power producer operating gas and renewable generation'],
  ['Fairmont Insurance', 'general insurer offering property, casualty and life products'],
  ['Oakline Software', 'enterprise software vendor selling subscription applications'],
  ['Riverbend Packaging', 'packaging manufacturer producing paper and corrugated containers']
];

const MUNICIPAL_BONDS = [
  ['Harborview Water District', 'water treatment and distribution infrastructure'],
  ['Maplewood School District', 'school construction and campus improvement programme'],
  ['Crestfield Transit Authority', 'bus rapid transit and rail carriage acquisitions'],
  ['Brookhaven Hospital Board', 'regional hospital expansion and equipment upgrades'],
  ['Lakeside Municipal Power', 'electric grid modernisation and substation upgrades'],
  ['Fairview County Road Fund', 'highway resurfacing and bridge maintenance work'],
  ['Ashford University Endowment', 'campus housing and academic building projects'],
  ['Port Meridian Terminal', 'cargo terminal dredging and container handling equipment'],
  ['Stonegate Fire District', 'fire station construction and emergency response vehicles'],
  ['Riverton Parking Authority', 'municipal parking structures and street improvement bonds']
];

const INDEX_LINKED_BONDS = [
  ['Illustrative Inflation-Linked Treasury Note (Sample)', 'DEMO-ILB-01', 'principal value indexed to a consumer price measure'],
  ['Illustrative CPI-Linked Corporate Note (Sample)', 'DEMO-ILB-02', 'coupon that adjusts with an inflation reference index'],
  ['Sample Real Return Bond 2032 (Demo)', 'DEMO-ILB-03', 'bond designed to preserve purchasing power over its term'],
  ['Illustrative Indexed Savings Note (Sample)', 'DEMO-ILB-04', 'retail-style note whose value tracks a price index'],
  ['Sample Inflation Floor Bond 2030 (Demo)', 'DEMO-ILB-05', 'indexed note with a minimum redemption value floor']
];

function buildBonds() {
  const rows = [];
  BOND_ISSUERS.forEach(([issuer, focus], i) => {
    const year = 2027 + ((i * 3) % 9);
    const kind = i % 3;
    const label = kind === 0 ? 'Note' : kind === 1 ? 'Senior Bond' : 'Debenture';
    rows.push({
      name: `${issuer} ${label} ${year} (Sample)`,
      symbol: `DEMO-CB-${String(i + 1).padStart(2, '0')}`,
      exchange: null,
      focus: `a fictional fixed-rate corporate ${label.toLowerCase()} from ${issuer}, a ${focus}`
    });
  });
  const tenors = ['2-Year', '5-Year', '7-Year', '10-Year', '30-Year'];
  for (let i = 0; i < 12; i++) {
    const tenor = tenors[i % tenors.length];
    const year = 2027 + i;
    rows.push({
      name: `Illustrative ${tenor} Treasury Note ${year} (Sample)`,
      symbol: `DEMO-GOV-${String(i + 1).padStart(2, '0')}`,
      exchange: null,
      focus: `a fictional sovereign ${tenor} government security modelled on treasury note structures`
    });
  }
  MUNICIPAL_BONDS.forEach(([issuer, focus], i) => {
    const year = 2027 + ((i * 2) % 8);
    rows.push({
      name: `Sample Municipal Revenue Bond ${issuer} ${year}`,
      symbol: `DEMO-MUNI-${String(i + 1).padStart(2, '0')}`,
      exchange: null,
      focus: `a fictional municipal revenue obligation funding ${focus}`
    });
  });
  INDEX_LINKED_BONDS.forEach(([name, symbol, focus], i) => {
    rows.push({
      name,
      symbol,
      exchange: null,
      focus: `a fictional inflation-sensitive debt instrument where ${focus}`
    });
  });
  return rows;
}

/* ------------------------------------------------------------------ */
/* Record assembly                                                     */
/* ------------------------------------------------------------------ */
const RANGE = {
  Stock: [6, 720],
  Bond: [88, 106],
  Commodity: [12, 2400],
  Cryptocurrency: [0.05, 62000],
  ETF: [18, 480],
  'Mutual Fund': [9, 260],
  'Real Estate': [8, 240]
};

const MARKET = {
  Stock: 'US Equities',
  Bond: 'Fixed Income (Sample)',
  Commodity: 'Commodities',
  Cryptocurrency: 'Digital Assets',
  ETF: 'Exchange-Traded Funds',
  'Mutual Fund': 'Mutual Funds',
  'Real Estate': 'Real Estate'
};

const ASSET_CLASS = {
  Stock: 'Equity',
  Bond: 'Fixed Income',
  Commodity: 'Commodity',
  Cryptocurrency: 'Cryptocurrency',
  ETF: 'ETF',
  'Mutual Fund': 'Mutual Fund',
  'Real Estate': 'Real Estate'
};

const TYPE_SLUG = {
  Stock: 'stock',
  Bond: 'bond',
  Commodity: 'commodity',
  Cryptocurrency: 'crypto',
  ETF: 'etf',
  'Mutual Fund': 'mutual-fund',
  'Real Estate': 'real-estate'
};

function randomPurchaseDate() {
  const start = Date.UTC(2015, 0, 1);
  const end = Date.UTC(2026, 8, 10);
  const t = new Date(start + rand() * (end - start));
  return t.toISOString().slice(0, 10);
}

function randomStatus(type, index) {
  const r = rand();
  // Only the supported permanent statuses exist: Active (held) and Sold (disposed).
  if (type === 'Bond') return r < 0.78 ? 'Active' : 'Sold';
  if (type === 'Cryptocurrency') return r < 0.8 ? 'Active' : 'Sold';
  if (type === 'Commodity') return r < 0.82 ? 'Active' : 'Sold';
  if (index % 17 === 3) return 'Active';
  return r < 0.7 ? 'Active' : 'Sold';
}

const counters = {};
const records = [];
const errors = [];

function addRecord(type, name, symbol, exchange, focus, demoInstrument) {
  counters[type] = (counters[type] || 0) + 1;
  const index = counters[type];

  const [minPrice, maxPrice] = RANGE[type];
  const purchasePrice = r2(rnd(minPrice, maxPrice));
  let currentPrice;
  if (type === 'Bond') {
    currentPrice = r2(purchasePrice * rnd(0.96, 1.07));
  } else {
    currentPrice = r2(purchasePrice * rnd(0.62, 2.15));
  }
  if (currentPrice <= 0) currentPrice = r2(purchasePrice);

  const targetValue = rint(2500, 24000);
  let quantity = Math.round(targetValue / currentPrice);
  quantity = Math.max(1, Math.min(5000, quantity));

  const purchaseDate = randomPurchaseDate();
  const status = randomStatus(type, index);

  const opener = OPENERS[type][(index - 1) % OPENERS[type].length];
  const context = CONTEXTS[type][(index * 5 + 2) % CONTEXTS[type].length];
  const disclaimer = demoInstrument ? DISCLAIMER_DEMO : DISCLAIMER_GENERIC;
  const symbolSuffix = symbol ? ` (${symbol})` : '';
  const description =
    `${opener}: ${name}${symbolSuffix} — ${focus}. ${context} ${disclaimer}`;

  const record = {
    seedKey: `seed-v1-${TYPE_SLUG[type]}-${String(index).padStart(3, '0')}`,
    name,
    symbol: symbol || null,
    exchange: exchange || null,
    market: MARKET[type],
    assetClass: ASSET_CLASS[type],
    currency: 'USD',
    description,
    type,
    purchasePrice,
    currentPrice,
    quantity,
    purchaseDate,
    status
  };

  // ---- validation of every generated record ----
  if (!record.name || record.name.length > 120) errors.push(`bad name: ${name}`);
  if (!record.description || record.description.length > 4000) errors.push(`bad description: ${name}`);
  if (record.symbol && record.symbol.length > 30) errors.push(`symbol too long: ${record.symbol}`);
  if (!(record.purchasePrice > 0)) errors.push(`bad purchasePrice: ${name}`);
  if (!(record.currentPrice > 0)) errors.push(`bad currentPrice: ${name}`);
  if (!(record.quantity >= 1)) errors.push(`bad quantity: ${name}`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(record.purchaseDate)) errors.push(`bad date: ${name}`);
  if (!['Active', 'Sold'].includes(record.status)) errors.push(`bad status: ${record.status} (${name})`);

  records.push(record);
  return record;
}

// ---- Stocks ----
STOCKS.forEach(([name, symbol, exchange, focus]) => {
  addRecord('Stock', name, symbol, exchange, `a listed company focused on ${focus}`, false);
});

// ---- Bonds ----
buildBonds().forEach((b) => {
  addRecord('Bond', b.name, b.symbol, b.exchange, `${b.focus}; every amount on this row is a fictional demo figure`, true);
});

// ---- Commodities ----
COMMODITIES.forEach(([name, symbol, focus]) => {
  addRecord('Commodity', `${name} (Sample Position)`, symbol, null,
    `a demonstration spot-style position in ${name.toLowerCase()}, a commodity where ${focus}`, false);
});

// ---- Cryptocurrencies ----
CRYPTOS.forEach(([name, symbol, focus]) => {
  addRecord('Cryptocurrency', `${name} (Sample Holding)`, symbol, null,
    `a demo wallet holding of ${name}, ${focus}`, false);
});

// ---- ETFs ----
ETFS.forEach(([name, symbol, exchange, focus]) => {
  addRecord('ETF', name, symbol, exchange, `an exchange-traded fund that ${focus}`, false);
});

// ---- Mutual funds ----
MUTUAL_FUNDS_REAL.forEach(([name, symbol, exchange, focus]) => {
  addRecord('Mutual Fund', name, symbol, exchange, `a pooled investment fund that ${focus}`, false);
});
MUTUAL_FUNDS_DEMO.forEach(([name, symbol, exchange, focus]) => {
  addRecord('Mutual Fund', name, symbol, exchange, `${focus}; the fund itself does not exist and exists only for testing`, true);
});

// ---- Real estate ----
REITS_REAL.forEach(([name, symbol, exchange, focus]) => {
  addRecord('Real Estate', name, symbol, exchange, `a listed property trust where ${focus}`, false);
});
REAL_ESTATE_DEMO.forEach(([name, symbol, exchange, focus]) => {
  addRecord('Real Estate', name, symbol, exchange, `${focus}; the vehicle itself is fictional and exists only for testing`, true);
});

/* ------------------------------------------------------------------ */
/* Dataset-level validation                                           */
/* ------------------------------------------------------------------ */
const seenKeys = new Set();
const seenSymbols = new Set();
const seenNames = new Set();
for (const r of records) {
  if (seenKeys.has(r.seedKey)) errors.push(`duplicate seedKey: ${r.seedKey}`);
  seenKeys.add(r.seedKey);
  if (r.symbol) {
    if (seenSymbols.has(r.symbol)) errors.push(`duplicate symbol: ${r.symbol}`);
    seenSymbols.add(r.symbol);
  }
  if (seenNames.has(r.name)) errors.push(`duplicate name: ${r.name}`);
  seenNames.add(r.name);
}

const uniqueDescriptions = new Set(records.map((r) => r.description));
if (uniqueDescriptions.size !== records.length) {
  errors.push(`duplicate descriptions: ${records.length - uniqueDescriptions.size}`);
}

if (records.length < TOTAL_REQUIRED) {
  errors.push(`only ${records.length} records generated, need >= ${TOTAL_REQUIRED}`);
}

if (errors.length) {
  console.error('VALIDATION FAILED:');
  errors.forEach((e) => console.error(' - ' + e));
  process.exit(1);
}

const dataset = {
  dataset: 'investtrack-seed-investments',
  version: 1,
  generatedOn: '2026-10-09',
  notice:
    'Illustrative demonstration data for the InvestTrack sample environment. Prices, quantities ' +
    'and valuations are fictional sample values - not live market prices, verified quotes, or ' +
    'guaranteed returns. Instruments marked (Sample), (Demo) or (Sample Holding) are fictional ' +
    'and are not officially issued financial instruments.',
  recordCount: records.length,
  investments: records
};

fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
fs.writeFileSync(OUT_FILE, JSON.stringify(dataset, null, 2) + '\n', 'utf8');

const summary = {};
records.forEach((r) => {
  summary[r.type] = (summary[r.type] || 0) + 1;
});
console.log('Wrote ' + OUT_FILE);
console.log('Total records: ' + records.length);
Object.keys(summary)
  .sort()
  .forEach((k) => console.log(`  ${k}: ${summary[k]}`));
console.log('Unique descriptions: ' + uniqueDescriptions.size);

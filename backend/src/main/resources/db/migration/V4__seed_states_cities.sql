-- Reference data: India's states/union territories and a representative set of major
-- cities in each, used to populate location pickers in the profile flow. This is genuine
-- reference data (an open, app-agnostic set that can grow over time), not a fixed small
-- enum -- hence real tables here rather than a CHECK-constrained column like playing_role.
--
-- `code` uses the common vehicle-registration-style abbreviation for each state/UT (e.g.
-- MH, KA, TN) since India has no single official ISO-3166-2-equivalent short code in
-- everyday use; these are the most widely recognized short forms.
--
-- Cities are not exhaustive -- state capitals, major metros, and cities large enough to
-- plausibly have local/district-level cricket leagues. state_code cascades on delete since
-- a state row is never removed without also removing the cities that belong to it.
CREATE TABLE states (
    code  VARCHAR PRIMARY KEY,
    name  VARCHAR NOT NULL
);

CREATE TABLE cities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_code  VARCHAR NOT NULL REFERENCES states(code) ON DELETE CASCADE,
    name        VARCHAR NOT NULL,
    UNIQUE (state_code, name)
);

-- 28 states
INSERT INTO states (code, name) VALUES
    ('AP', 'Andhra Pradesh'),
    ('AR', 'Arunachal Pradesh'),
    ('AS', 'Assam'),
    ('BR', 'Bihar'),
    ('CG', 'Chhattisgarh'),
    ('GA', 'Goa'),
    ('GJ', 'Gujarat'),
    ('HR', 'Haryana'),
    ('HP', 'Himachal Pradesh'),
    ('JH', 'Jharkhand'),
    ('KA', 'Karnataka'),
    ('KL', 'Kerala'),
    ('MP', 'Madhya Pradesh'),
    ('MH', 'Maharashtra'),
    ('MN', 'Manipur'),
    ('ML', 'Meghalaya'),
    ('MZ', 'Mizoram'),
    ('NL', 'Nagaland'),
    ('OD', 'Odisha'),
    ('PB', 'Punjab'),
    ('RJ', 'Rajasthan'),
    ('SK', 'Sikkim'),
    ('TN', 'Tamil Nadu'),
    ('TS', 'Telangana'),
    ('TR', 'Tripura'),
    ('UP', 'Uttar Pradesh'),
    ('UK', 'Uttarakhand'),
    ('WB', 'West Bengal');

-- 8 union territories
INSERT INTO states (code, name) VALUES
    ('AN', 'Andaman and Nicobar Islands'),
    ('CH', 'Chandigarh'),
    ('DN', 'Dadra and Nagar Haveli and Daman and Diu'),
    ('DL', 'Delhi'),
    ('JK', 'Jammu and Kashmir'),
    ('LA', 'Ladakh'),
    ('LD', 'Lakshadweep'),
    ('PY', 'Puducherry');

-- Major cities per state/UT (state capitals + major metros / large cricket-playing cities)
INSERT INTO cities (state_code, name) VALUES
    ('AP', 'Amaravati'),
    ('AP', 'Visakhapatnam'),
    ('AP', 'Vijayawada'),
    ('AP', 'Guntur'),
    ('AP', 'Tirupati'),

    ('AR', 'Itanagar'),

    ('AS', 'Guwahati'),
    ('AS', 'Dispur'),
    ('AS', 'Silchar'),
    ('AS', 'Dibrugarh'),

    ('BR', 'Patna'),
    ('BR', 'Gaya'),
    ('BR', 'Bhagalpur'),
    ('BR', 'Muzaffarpur'),

    ('CG', 'Raipur'),
    ('CG', 'Bilaspur'),
    ('CG', 'Durg'),

    ('GA', 'Panaji'),
    ('GA', 'Margao'),
    ('GA', 'Vasco da Gama'),

    ('GJ', 'Gandhinagar'),
    ('GJ', 'Ahmedabad'),
    ('GJ', 'Surat'),
    ('GJ', 'Vadodara'),
    ('GJ', 'Rajkot'),

    ('HR', 'Gurugram'),
    ('HR', 'Faridabad'),
    ('HR', 'Panipat'),
    ('HR', 'Rohtak'),

    ('HP', 'Shimla'),
    ('HP', 'Dharamshala'),

    ('JH', 'Ranchi'),
    ('JH', 'Jamshedpur'),
    ('JH', 'Dhanbad'),
    ('JH', 'Bokaro Steel City'),

    ('KA', 'Bengaluru'),
    ('KA', 'Mysuru'),
    ('KA', 'Hubballi'),
    ('KA', 'Mangaluru'),
    ('KA', 'Belagavi'),

    ('KL', 'Thiruvananthapuram'),
    ('KL', 'Kochi'),
    ('KL', 'Kozhikode'),
    ('KL', 'Thrissur'),

    ('MP', 'Bhopal'),
    ('MP', 'Indore'),
    ('MP', 'Gwalior'),
    ('MP', 'Jabalpur'),

    ('MH', 'Mumbai'),
    ('MH', 'Pune'),
    ('MH', 'Nagpur'),
    ('MH', 'Nashik'),
    ('MH', 'Thane'),
    ('MH', 'Aurangabad'),

    ('MN', 'Imphal'),

    ('ML', 'Shillong'),

    ('MZ', 'Aizawl'),

    ('NL', 'Kohima'),
    ('NL', 'Dimapur'),

    ('OD', 'Bhubaneswar'),
    ('OD', 'Cuttack'),
    ('OD', 'Rourkela'),

    ('PB', 'Amritsar'),
    ('PB', 'Ludhiana'),
    ('PB', 'Jalandhar'),
    ('PB', 'Patiala'),

    ('RJ', 'Jaipur'),
    ('RJ', 'Jodhpur'),
    ('RJ', 'Udaipur'),
    ('RJ', 'Kota'),

    ('SK', 'Gangtok'),

    ('TN', 'Chennai'),
    ('TN', 'Coimbatore'),
    ('TN', 'Madurai'),
    ('TN', 'Tiruchirappalli'),
    ('TN', 'Salem'),

    ('TS', 'Hyderabad'),
    ('TS', 'Warangal'),
    ('TS', 'Nizamabad'),

    ('TR', 'Agartala'),

    ('UP', 'Lucknow'),
    ('UP', 'Kanpur'),
    ('UP', 'Varanasi'),
    ('UP', 'Agra'),
    ('UP', 'Noida'),
    ('UP', 'Ghaziabad'),
    ('UP', 'Meerut'),

    ('UK', 'Dehradun'),
    ('UK', 'Haridwar'),
    ('UK', 'Haldwani'),

    ('WB', 'Kolkata'),
    ('WB', 'Howrah'),
    ('WB', 'Siliguri'),
    ('WB', 'Durgapur'),

    ('AN', 'Port Blair'),

    ('CH', 'Chandigarh'),

    ('DN', 'Daman'),
    ('DN', 'Silvassa'),

    ('DL', 'New Delhi'),

    ('JK', 'Srinagar'),
    ('JK', 'Jammu'),

    ('LA', 'Leh'),
    ('LA', 'Kargil'),

    ('LD', 'Kavaratti'),

    ('PY', 'Puducherry');

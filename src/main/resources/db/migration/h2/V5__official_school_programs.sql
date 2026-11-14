-- Official admission snapshots. Stable identifiers make manual import and Flyway replay safe.
INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000001', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000001","schoolName":"上海交通大学","programName":"081200 计算机科学与技术 · 033 计算机学院","major":"计算机科学与技术","region":"上海","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":4,"admissionRatio":null,"sources":[{"title":"上海交通大学2027年全日制硕士研究生统考分专业招生计划表（校本部）","url":"https://yzb.sjtu.edu.cn/post/3808","publisher":"上海交通大学研究生招生网","version":"official-2027-exam-plan","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000001"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000001');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000002', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000002","schoolName":"上海交通大学","programName":"085400 电子信息 · 033 计算机学院","major":"电子信息","region":"上海","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":208,"admissionRatio":null,"sources":[{"title":"上海交通大学2027年全日制硕士研究生统考分专业招生计划表（校本部）","url":"https://yzb.sjtu.edu.cn/post/3808","publisher":"上海交通大学研究生招生网","version":"official-2027-exam-plan","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000002"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000002');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000003', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000003","schoolName":"上海交通大学","programName":"040100 教育学 · 350 教育学院","major":"教育学","region":"上海","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":6,"admissionRatio":null,"sources":[{"title":"上海交通大学2027年全日制硕士研究生统考分专业招生计划表（校本部）","url":"https://yzb.sjtu.edu.cn/post/3808","publisher":"上海交通大学研究生招生网","version":"official-2027-exam-plan","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000003"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000003');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000004', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000004","schoolName":"上海交通大学","programName":"025100 金融 · 380 上海高级金融学院","major":"金融","region":"上海","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":13,"admissionRatio":null,"sources":[{"title":"上海交通大学2027年全日制硕士研究生统考分专业招生计划表（校本部）","url":"https://yzb.sjtu.edu.cn/post/3808","publisher":"上海交通大学研究生招生网","version":"official-2027-exam-plan","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000004"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000004');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000005', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000005","schoolName":"东南大学","programName":"025600 资产评估 · 雷恩研究生学院","major":"资产评估","region":"江苏","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":null,"admissionRatio":null,"sources":[{"title":"东南大学雷恩研究生学院2027年硕士研究生招生简章","url":"https://yzb.seu.edu.cn/2026/1010/c6679a585467/page.htm","publisher":"东南大学研究生招生网","version":"official-2027-prospectus","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000005"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000005');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000006', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000006","schoolName":"东南大学","programName":"085404 计算机技术 · 雷恩研究生学院","major":"计算机技术","region":"江苏","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":null,"admissionRatio":null,"sources":[{"title":"东南大学雷恩研究生学院2027年硕士研究生招生简章","url":"https://yzb.seu.edu.cn/2026/1010/c6679a585467/page.htm","publisher":"东南大学研究生招生网","version":"official-2027-prospectus","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000006"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000006');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000007', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000007","schoolName":"东南大学","programName":"085602 化学工程 · 雷恩研究生学院","major":"化学工程","region":"江苏","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":null,"admissionRatio":null,"sources":[{"title":"东南大学雷恩研究生学院2027年硕士研究生招生简章","url":"https://yzb.seu.edu.cn/2026/1010/c6679a585467/page.htm","publisher":"东南大学研究生招生网","version":"official-2027-prospectus","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000007"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000007');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000008', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000008","schoolName":"东南大学","programName":"035102 法律（法学） · 雷恩研究生学院","major":"法律（法学）","region":"江苏","year":2027,"difficulty":"medium","scoreLine":null,"enrollment":null,"admissionRatio":null,"sources":[{"title":"东南大学雷恩研究生学院2027年硕士研究生招生简章","url":"https://yzb.seu.edu.cn/2026/1010/c6679a585467/page.htm","publisher":"东南大学研究生招生网","version":"official-2027-prospectus","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000008"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000008');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000009', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000009","schoolName":"东南大学","programName":"081200 计算机科学与技术 · 009 计算机科学与工程学院","major":"计算机科学与技术","region":"江苏","year":2026,"difficulty":"medium","scoreLine":376,"enrollment":null,"admissionRatio":null,"sources":[{"title":"2026年东南大学各院系所复试分数线","url":"https://yzb.seu.edu.cn/2026/0928/c6674a584558/page.htm","publisher":"东南大学研究生招生网","version":"official-2026-retest-cutoff","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000009"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000009');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000010', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000010","schoolName":"东南大学","programName":"083500 软件工程 · 009 计算机科学与工程学院","major":"软件工程","region":"江苏","year":2026,"difficulty":"medium","scoreLine":355,"enrollment":null,"admissionRatio":null,"sources":[{"title":"2026年东南大学各院系所复试分数线","url":"https://yzb.seu.edu.cn/2026/0928/c6674a584558/page.htm","publisher":"东南大学研究生招生网","version":"official-2026-retest-cutoff","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000010"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000010');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000011', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000011","schoolName":"东南大学","programName":"085404 计算机技术 · 009 计算机科学与工程学院","major":"计算机技术","region":"江苏","year":2026,"difficulty":"medium","scoreLine":344,"enrollment":null,"admissionRatio":null,"sources":[{"title":"2026年东南大学各院系所复试分数线","url":"https://yzb.seu.edu.cn/2026/0928/c6674a584558/page.htm","publisher":"东南大学研究生招生网","version":"official-2026-retest-cutoff","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000011"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000011');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000012', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000012","schoolName":"东南大学","programName":"085410 人工智能 · 009 计算机科学与工程学院","major":"人工智能","region":"江苏","year":2026,"difficulty":"medium","scoreLine":347,"enrollment":null,"admissionRatio":null,"sources":[{"title":"2026年东南大学各院系所复试分数线","url":"https://yzb.seu.edu.cn/2026/0928/c6674a584558/page.htm","publisher":"东南大学研究生招生网","version":"official-2026-retest-cutoff","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000012"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000012');

INSERT INTO programs (id, owner_id, version, payload)
SELECT '75000000-0000-4000-8000-000000000013', 'public', 1, '{"id":"75000000-0000-4000-8000-000000000013","schoolName":"东南大学","programName":"140500 智能科学与技术 · 009 计算机科学与工程学院","major":"智能科学与技术","region":"江苏","year":2026,"difficulty":"medium","scoreLine":349,"enrollment":null,"admissionRatio":null,"sources":[{"title":"2026年东南大学各院系所复试分数线","url":"https://yzb.seu.edu.cn/2026/0928/c6674a584558/page.htm","publisher":"东南大学研究生招生网","version":"official-2026-retest-cutoff","publishedAt":null,"retrievedAt":"2026-11-10T00:00:00Z"}],"snapshotId":"75000000-0000-4000-9000-000000000013"}'
WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '75000000-0000-4000-8000-000000000013');

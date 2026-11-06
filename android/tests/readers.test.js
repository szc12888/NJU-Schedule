const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const asset = name => fs.readFileSync(
  path.join(__dirname, '../app/src/main/assets', name + '.js'),
  'utf8'
);

async function run(name, fetch, origin = 'https://lms.nju.edu.cn', extra = {}) {
  const parsedOrigin = new URL(origin);
  const sandbox = {
    window: {},
    location: {
      origin: parsedOrigin.origin,
      hostname: parsedOrigin.hostname,
      pathname: parsedOrigin.pathname,
      href: origin
    },
    URL,
    URLSearchParams,
    fetch,
    ...extra
  };
  vm.runInNewContext(asset(name), sandbox);
  for (let index = 0; index < 200; index++) {
    await new Promise(resolve => setImmediate(resolve));
    const result = sandbox.window[
      name === 'grades' ? '__njuGradeResult' : '__njuHomeworkResult'
    ];
    if (result) {
      return JSON.parse(JSON.stringify(result));
    }
  }
  throw new Error('reader did not settle');
}

function response(url, json, options = {}) {
  const origin = options.origin || 'https://lms.nju.edu.cn';
  const rest = {...options};
  delete rest.origin;
  return {
    ok: true,
    status: 200,
    url: new URL(url, origin).href,
    json: async () => json,
    ...rest
  };
}

function textResponse(url, text, options = {}) {
  return response(url, null, {
    ...options,
    text: async () => text
  });
}

function node(textContent = '') {
  return {
    textContent,
    contains: () => false,
    querySelector: () => null,
    querySelectorAll: () => []
  };
}

function moodleDocuments() {
  const link = {
    textContent: 'Synthetic assignment',
    getAttribute: () => '/mod/assign/view.php?id=42'
  };
  const titleCell = {...node('Synthetic assignment'), contains: value => value === link};
  const dueCell = node('2026-11-10');
  const statusCell = node('Submitted for grading');
  const gradeCell = node('88');
  const header = {
    querySelector: () => null,
    querySelectorAll: selector => selector === 'th,td'
      ? [node('Assignment'), node('Due date'), node('Submission status'), node('Grade')]
      : []
  };
  const assignmentRow = {
    querySelector: selector => selector.includes('a[href')
      ? link
      : null,
    querySelectorAll: selector => selector === 'td'
      ? [titleCell, dueCell, statusCell, gradeCell]
      : []
  };
  const table = {
    querySelector: selector => {
      if (selector.includes('a[href')) return link;
      if (selector === 'thead tr' || selector === 'tr') return header;
      return null;
    },
    querySelectorAll: selector => selector === 'tr'
      ? [header, assignmentRow]
      : []
  };

  const attachment = node('synthetic.pdf');
  const intro = {
    ...node('Synthetic description'),
    querySelectorAll: selector => selector.includes('pluginfile.php')
      ? [attachment]
      : []
  };
  const feedbackCell = node('Synthetic feedback');
  const feedbackRow = {
    querySelectorAll: selector => selector === 'th,td'
      ? [node('Feedback comments'), feedbackCell]
      : []
  };
  const coursePage = {
    querySelector: selector => {
      if (/input\[name="username"\]/.test(selector)) return null;
      return null;
    },
    querySelectorAll: selector => selector === 'table' ? [table] : [],
    body: node('')
  };
  const detailPage = {
    querySelector: selector => {
      if (selector === '#intro, .activity-description') return intro;
      if (/input\[name="username"\]/.test(selector)) return null;
      return null;
    },
    querySelectorAll: selector => {
      if (selector.includes('pluginfile.php')) return [attachment];
      if (selector.includes('.feedback table tr')) return [feedbackRow];
      return [];
    }
  };
  return {
    'course-page': coursePage,
    'detail-page': detailPage,
    'login-page': {
      querySelector: selector => /input\[name="username"\]/.test(selector) ? node() : null,
      querySelectorAll: () => [],
      body: node('')
    }
  };
}

function moodleParser(pages) {
  return class {
    parseFromString(html) {
      if (!pages[html]) throw new Error('unexpected synthetic HTML');
      return pages[html];
    }
  };
}

function moodleSandbox(pages = moodleDocuments()) {
  return {
    document: {
      querySelector: selector => selector.includes('.usermenu') ? node() : null
    },
    window: {M: {cfg: {sesskey: 'synthetic-sesskey'}}},
    DOMParser: moodleParser(pages)
  };
}

const lmsOrigin = 'https://lms.nju.edu.cn';

test('LMS reads every page and preserves stable IDs and boolean state', async () => {
  const calls = [];
  const result = await run('lms', async url => {
    calls.push(url);
    if (url === '/api/my-courses') {
      return response(url, {pages: 1, courses: [{id: 1, name: 'Synthetic'}]});
    }
    const page = new URL(url, lmsOrigin).searchParams.get('page');
    return response(url, {
      pages: 2,
      homework_activities: [{
        id: Number(page),
        title: 'Synthetic ' + page,
        submitted: page === '1',
        deadline: null
      }]
    });
  });

  assert.equal(result.partial, undefined);
  assert.equal(result.courses[0].id, '1');
  assert.equal(result.courses[0].assignments.length, 2);
  assert.equal(result.courses[0].assignments[0].id, '1');
  assert.equal(result.courses[0].assignments[1].id, '2');
  assert.equal(result.courses[0].assignments[1].submitted, false);
  assert.equal(calls.filter(url => url.includes("homework-activities") || url === "/api/my-courses").length, 3);
});

test('LMS failed later page is partial and does not retain a false snapshot', async () => {
  const result = await run('lms', async url => {
    if (url === '/api/my-courses') {
      return response(url, {courses: [{id: 1, name: 'Synthetic'}]});
    }
    return response(url, {
      pages: 2,
      homework_activities: [{id: 1, title: 'Synthetic'}]
    }, {
      ok: !url.includes('page=2'),
      status: url.includes('page=2') ? 503 : 200
    });
  });

  assert.equal(result.partial, true);
  assert.match(result.courses[0].error, /HTTP 503/);
  assert.deepEqual(result.courses[0].assignments, []);
});

test('LMS refuses a multi-page course list without inventing a page parameter', async () => {
  const calls = [];
  const result = await run('lms', async url => {
    calls.push(url);
    return response(url, {
      pages: 2,
      courses: [{id: 1, name: 'Synthetic'}]
    });
  });

  assert.match(result.error, /多页/);
  assert.equal(calls.length, 1);
});

test('LMS rejects inconsistent pagination instead of claiming completion', async () => {
  const result = await run('lms', async url => {
    if (url === '/api/my-courses') {
      return response(url, {courses: [{id: 1, name: 'Synthetic'}]});
    }
    const page = new URL(url, lmsOrigin).searchParams.get('page');
    return response(url, {
      pages: page === '1' ? 3 : 2,
      homework_activities: [{id: page, title: 'Synthetic ' + page}]
    });
  });

  assert.equal(result.partial, true);
  assert.match(result.courses[0].error, /分页总数/);
});

test('LMS rejects missing course and assignment identifiers', async () => {
  let calls = [];
  const missingCourse = await run('lms', async url => {
    calls.push(url);
    return response(url, {courses: [{name: 'Synthetic'}]});
  });
  assert.ok(missingCourse.error);
  assert.equal(calls.length, 1);

  const missingAssignment = await run('lms', async url => {
    if (url === '/api/my-courses') {
      return response(url, {courses: [{id: 'course-1', name: 'Synthetic'}]});
    }
    return response(url, {
      pages: 1,
      homework_activities: [{id: '', title: 'Synthetic'}]
    });
  });
  assert.equal(missingAssignment.partial, true);
  assert.match(missingAssignment.courses[0].error, /稳定标识/);
});

test('LMS authentication HTML and redirects cannot become empty success', async () => {
  const html = await run('lms', async url => response(url, null, {
    json: async () => {
      throw new Error('synthetic html');
    }
  }));
  assert.ok(html.error);

  const redirect = await run('lms', async url => response(url, null, {
    url: 'https://lms.nju.edu.cn/login'
  }));
  assert.ok(redirect.error);
});

test('LMS refuses another origin before fetching', async () => {
  let calls = 0;
  const result = await run('lms', async () => {
    calls++;
  }, 'https://authserver.nju.edu.cn/');
  assert.ok(result.error);
  assert.equal(calls, 0);
});

test('Moodle reads the verified index and detail links without invented APIs', async () => {
  const calls = [];
  const result = await run('moodle', async (url, options) => {
    calls.push({url, options});
    if (url.startsWith('/lib/ajax/service.php')) {
      const request = JSON.parse(options.body);
      assert.equal(
        request[0].methodname,
        'core_course_get_enrolled_courses_by_timeline_classification'
      );
      assert.deepEqual(request[0].args, {classification: 'all', limit: 0, offset: 0});
      return response(url, [{
        error: false,
        data: {
          nextoffset: 0,
          courses: [{id: 7, fullname: 'Synthetic course'}]
        }
      }], {origin: 'https://selearning.nju.edu.cn'});
    }
    if (url.includes('/mod/assign/index.php')) {
      return textResponse(url, 'course-page', {
        origin: 'https://selearning.nju.edu.cn'
      });
    }
    return textResponse(url, 'detail-page', {
      origin: 'https://selearning.nju.edu.cn'
    });
  }, 'https://selearning.nju.edu.cn/my/', moodleSandbox());

  assert.equal(result.partial, undefined);
  assert.equal(result.courses[0].id, '7');
  assert.equal(result.courses[0].assignments[0].id, '42');
  assert.equal(result.courses[0].assignments[0].submitted, true);
  assert.equal(result.courses[0].assignments[0].grade, '88');
  assert.equal(result.courses[0].assignments[0].content, 'Synthetic description');
  assert.deepEqual(result.courses[0].assignments[0].attachments, ['synthetic.pdf']);
  assert.equal(result.courses[0].assignments[0].comment, 'Synthetic feedback');
  assert.equal(calls.length, 3);
});

test('Moodle absent detail fields stay empty without blocking the index', async () => {
  const pages = moodleDocuments();
  pages['detail-page'] = {
    querySelector: () => null,
    querySelectorAll: () => []
  };
  const result = await run('moodle', async url => {
    if (url.startsWith('/lib/ajax/service.php')) {
      return response(url, [{
        error: false,
        data: {courses: [{id: 7, fullname: 'Synthetic course'}]}
      }], {origin: 'https://selearning.nju.edu.cn'});
    }
    if (url.includes('/mod/assign/index.php')) {
      return textResponse(url, 'course-page', {
        origin: 'https://selearning.nju.edu.cn'
      });
    }
    return textResponse(url, 'detail-page', {
      origin: 'https://selearning.nju.edu.cn'
    });
  }, 'https://selearning.nju.edu.cn/my/', moodleSandbox(pages));

  const course = result.courses[0];
  assert.equal(result.partial, undefined);
  assert.equal(course.error, undefined);
  assert.equal(course.assignments.length, 1);
  assert.equal(course.assignments[0].content, '');
  assert.equal(course.assignments[0].detailUnavailable, undefined);
});

test('Moodle optional detail failure keeps the complete index and marks detailsPartial', async () => {
  const result = await run('moodle', async url => {
    if (url.startsWith('/lib/ajax/service.php')) {
      return response(url, [{
        error: false,
        data: {courses: [{id: 7, fullname: 'Synthetic course'}]}
      }], {origin: 'https://selearning.nju.edu.cn'});
    }
    if (url.includes('/mod/assign/index.php')) {
      return textResponse(url, 'course-page', {
        origin: 'https://selearning.nju.edu.cn'
      });
    }
    return textResponse(url, 'detail-page', {
      ok: false,
      status: 503,
      origin: 'https://selearning.nju.edu.cn'
    });
  }, 'https://selearning.nju.edu.cn/my/', moodleSandbox());

  const course = result.courses[0];
  assert.equal(result.partial, true);
  assert.equal(course.error, undefined);
  assert.equal(course.detailsPartial.count, 1);
  assert.deepEqual(course.detailsPartial.assignmentIds, ['42']);
  assert.match(course.detailsPartial.reasons[0].reason, /HTTP 503/);
  assert.equal(course.assignments.length, 1);
  assert.equal(course.assignments[0].id, '42');
  assert.equal(course.assignments[0].title, 'Synthetic assignment');
  assert.equal(course.assignments[0].status, 'Submitted for grading');
  assert.equal(course.assignments[0].grade, '88');
  assert.equal(course.assignments[0].detailUnavailable, true);
});

test('Moodle detail authentication failure keeps index data but protects the course', async () => {
  const result = await run('moodle', async url => {
    if (url.startsWith('/lib/ajax/service.php')) {
      return response(url, [{
        error: false,
        data: {courses: [{id: 7, fullname: 'Synthetic course'}]}
      }], {origin: 'https://selearning.nju.edu.cn'});
    }
    if (url.includes('/mod/assign/index.php')) {
      return textResponse(url, 'course-page', {
        origin: 'https://selearning.nju.edu.cn'
      });
    }
    return textResponse(url, 'detail-page', {
      url: '/login/index.php',
      origin: 'https://selearning.nju.edu.cn'
    });
  }, 'https://selearning.nju.edu.cn/my/', moodleSandbox());

  const course = result.courses[0];
  assert.equal(result.partial, true);
  assert.match(course.error, /登录已失效/);
  assert.equal(course.assignments.length, 1);
  assert.equal(course.assignments[0].detailUnavailable, true);
});

test('Moodle detects a login document served for an assignment index', async () => {
  const pages = moodleDocuments();
  const result = await run('moodle', async url => {
    if (url.startsWith('/lib/ajax/service.php')) {
      return response(url, [{
        error: false,
        data: {courses: [{id: 7, fullname: 'Synthetic course'}]}
      }], {origin: 'https://selearning.nju.edu.cn'});
    }
    return textResponse(url, 'login-page', {
      origin: 'https://selearning.nju.edu.cn'
    });
  }, 'https://selearning.nju.edu.cn/my/', moodleSandbox(pages));

  assert.equal(result.partial, true);
  assert.match(result.courses[0].error, /登录已失效/);
});

test('Moodle unauthenticated page and missing course IDs fail explicitly', async () => {
  const unauthenticated = await run('moodle', async () => {
    throw new Error('should not fetch');
  }, 'https://selearning.nju.edu.cn/login/index.php', {
    document: {querySelector: () => null}
  });
  assert.ok(unauthenticated.error);

  const missingCourse = await run('moodle', async url => response(url, [{
    error: false,
    data: {courses: [{fullname: 'Synthetic course'}]}
  }], {origin: 'https://selearning.nju.edu.cn'}), 'https://selearning.nju.edu.cn/my/', {
    ...moodleSandbox(),
    DOMParser: moodleParser(moodleDocuments())
  });
  assert.ok(missingCourse.error);
});

async function runMoodleCoursePages(pages) {
  const offsets = [];
  let indexRequests = 0;
  const result = await run('moodle', async (url, options) => {
    if (url.startsWith('/lib/ajax/service.php')) {
      const request = JSON.parse(options.body)[0];
      assert.equal(request.methodname,
        'core_course_get_enrolled_courses_by_timeline_classification');
      assert.equal(request.args.classification, 'all');
      assert.equal(request.args.limit, 0);
      offsets.push(request.args.offset);
      const data = typeof pages === 'function'
        ? pages(request.args.offset)
        : pages[offsets.length - 1];
      assert.ok(data, 'unexpected extra course page');
      return response(url, [{error: false, data}], {
        origin: 'https://selearning.nju.edu.cn'
      });
    }
    if (url.includes('/mod/assign/index.php')) {
      indexRequests++;
      return textResponse(url, 'course-page', {
        origin: 'https://selearning.nju.edu.cn'
      });
    }
    return textResponse(url, 'detail-page', {
      origin: 'https://selearning.nju.edu.cn'
    });
  }, 'https://selearning.nju.edu.cn/my/', moodleSandbox());
  return {result, offsets, indexRequests};
}

test('Moodle limit zero follows nextoffset to an empty page with the same cursor', async () => {
  const {result, offsets} = await runMoodleCoursePages([
    {nextoffset: 7, courses: [{id: 7, fullname: 'Synthetic course'}]},
    {nextoffset: 7, courses: []}
  ]);
  assert.deepEqual(offsets, [0, 7]);
  assert.equal(result.error, undefined);
  assert.equal(result.partial, undefined);
  assert.deepEqual(result.courses.map(course => course.id), ['7']);
  assert.equal(result.courses[0].assignments.length, 1);
});

test('Moodle follows advancing cursors across all course pages', async () => {
  const {result, offsets} = await runMoodleCoursePages([
    {nextoffset: 5, courses: [{id: 7}]},
    {nextoffset: 12, courses: [{id: 8}, {id: 9}]},
    {nextoffset: 12, courses: []}
  ]);
  assert.deepEqual(offsets, [0, 5, 12]);
  assert.equal(result.error, undefined);
  assert.equal(result.partial, undefined);
  assert.deepEqual(result.courses.map(course => course.id), ['7', '8', '9']);
  assert.ok(result.courses.every(course => course.assignments.length === 1));
});

test('Moodle repeated course pages fail before reading assignment indexes', async () => {
  const {result, offsets, indexRequests} = await runMoodleCoursePages([
    {nextoffset: 1, courses: [{id: 7}]},
    {nextoffset: 2, courses: [{id: 7}]}
  ]);
  assert.deepEqual(offsets, [0, 1]);
  assert.match(result.error, /重复标识/);
  assert.equal(result.courses, undefined);
  assert.equal(indexRequests, 0);
});

test('Moodle rejects non-progressing and invalid course cursors', async () => {
  for (const nextoffset of [4, 3, -1, 1.5, '5', false]) {
    const {result, indexRequests} = await runMoodleCoursePages([
      {nextoffset: 4, courses: [{id: 7}]},
      {nextoffset, courses: [{id: 8}]}
    ]);
    assert.match(result.error, /分页偏移/);
    assert.equal(result.courses, undefined);
    assert.equal(indexRequests, 0);
  }
});

test('Moodle bounds course traversal to 100 requests without false success', async () => {
  const {result, offsets, indexRequests} = await runMoodleCoursePages(offset => ({
    nextoffset: offset + 1,
    courses: [{id: offset + 1}]
  }));
  assert.equal(offsets.length, 100);
  assert.match(result.error, /100 页/);
  assert.equal(result.courses, undefined);
  assert.equal(indexRequests, 0);
});

test('Moodle accepts legacy unlimited course responses with absent or zero cursor', async () => {
  for (const cursor of [{}, {nextoffset: 0}]) {
    const {result, offsets} = await runMoodleCoursePages([
      {...cursor, courses: [{id: 7}]}
    ]);
    assert.deepEqual(offsets, [0]);
    assert.equal(result.error, undefined);
    assert.equal(result.courses.length, 1);
  }
});

test('grade adapter refuses an unrelated host', async () => {
  const result = await run('grades', async () => {
    throw new Error('should not fetch');
  }, 'https://lms.nju.edu.cn/');
  assert.ok(result.error);
});

// Synthetic LMS responses matching the structure verified on Android; no personal data.
const lmsDom = class {parseFromString(value) {return {body: {textContent: value.replace(/<[^>]*>/g, '')}, querySelectorAll: () => []};}};
async function lmsFeedback({hidden=false, fail=false, score=0, comments=true, ungraded=false}={}) {
  return run('lms', async url => {
    if(url==='/api/my-courses')return response(url,{courses:[{id:7,name:'Synthetic'}]});
    if(url.includes('homework-activities'))return response(url,{pages:1,homework_activities:[{id:8,title:'Synthetic',submitted:true,score:'synthetic-index',is_announce_score_time_passed:!hidden,data:{description:'<p>Synthetic body</p>'}}]});
    if(url==='/api/activities/8')return response(url,{id:8,data:{description:'<p>Synthetic full body</p>'},uploads:[{name:'synthetic.txt'}],can_show_score:!hidden});
    assert.equal(url,'/api/course/activities/8/students/9/submission');
    if(fail)return response(url,null,{ok:false,status:503});
    return response(url,{activity_id:8,score:ungraded?null:score,...(ungraded?{}:{final_score:score}),is_announce_score_time_passed:!hidden,instructor_comment:comments?'Synthetic feedback':'',submission_correct:{comment:comments?'Synthetic feedback':''}});
  },lmsOrigin,{window:{globalData:{user:{id:9}}},DOMParser:lmsDom});
}
test('LMS reads final score including zero, HTML description, attachments and deduplicated teacher feedback',async()=>{
 const r=await lmsFeedback();const a=r.courses[0].assignments[0];
 assert.equal(a.grade,'0');assert.equal(a.content,'Synthetic full body');assert.deepEqual(a.attachments,['synthetic.txt']);assert.equal(a.comment,'Synthetic feedback');assert.equal(a.detailUnavailable,false);
});
test('LMS respects platform score visibility and preserves empty feedback',async()=>{
 const r=await lmsFeedback({hidden:true});const a=r.courses[0].assignments[0];assert.equal(a.grade,'');assert.equal(a.comment,'');assert.equal(a.detailUnavailable,false);
 const empty=await lmsFeedback({comments:false});assert.equal(empty.courses[0].assignments[0].comment,'');
});
test('LMS optional feedback failure keeps assignment snapshot with partial details',async()=>{
 const r=await lmsFeedback({fail:true});assert.equal(r.partial,undefined);assert.equal(r.courses[0].detailsPartial,true);assert.equal(r.courses[0].assignments[0].detailUnavailable,true);assert.equal(r.courses[0].assignments.length,1);
});

test('LMS submitted but ungraded response can omit final_score without failing details',async()=>{
 const r=await lmsFeedback({ungraded:true,comments:false});const a=r.courses[0].assignments[0];assert.equal(a.grade,'');assert.equal(a.detailUnavailable,false);assert.equal(r.courses[0].detailsPartial,undefined);
});

window.__njuHomeworkResult = null;
Promise.resolve(
(async () => {
  const origin = 'https://selearning.nju.edu.cn';
  if (location.origin !== origin) {
    return {error: '请回到 SElearning 页面。'};
  }

  const clean = value => String(value || '').replace(/\s+/g, ' ').trim();
  const bodyText = node => {
    if (!node) return '';
    if (!node.cloneNode) return clean(node.textContent);
    const copy = node.cloneNode(true);
    copy.querySelectorAll('script,style,[id^="assign_files_tree"],.fileuploadsubmission,.fileuploadsubmissiontime').forEach(item => item.remove());
    copy.querySelectorAll('li').forEach(item => {
      const parent = item.parentElement;
      if (parent?.tagName === 'OL') {
        const siblings = [...parent.children].filter(child => child.tagName === 'LI');
        const start = Number(parent.getAttribute('start') || 1);
        item.prepend(String((Number.isFinite(start) ? start : 1) + siblings.indexOf(item)) + '. ');
      } else item.prepend('• ');
    });
    copy.querySelectorAll('br').forEach(item => item.replaceWith('\n'));
    copy.querySelectorAll('p,div,li,h1,h2,h3,h4,tr').forEach(item => item.append('\n'));
    return (copy.textContent || '').replace(/[ \t]+/g, ' ').replace(/ *\n */g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  };
  const stableId = value => value == null ? '' : String(value).trim();
  const errorMessage = error => error?.message || '未知错误';
  const readerError = (message, kind, status = 0) =>
    Object.assign(new Error(message), {kind, status});
  const isLoginPath = value => /\/login(?:\/|$)/i.test(value);

  if (isLoginPath(location.pathname) ||
      !document.querySelector('.usermenu, [data-region="usermenu"], .userpicture')) {
    return {error: '尚未检测到已登录状态，请先完成登录并进入 Moodle 首页。'};
  }

  const config = window.M || window.moodleConfig;
  const sesskey = config?.cfg?.sesskey;
  if (!sesskey) {
    return {error: '页面没有提供 Moodle 会话信息，请刷新 Moodle 首页后重试。'};
  }

  const responseUrl = (response, fallback) => {
    try {
      return new URL(response.url || fallback, origin);
    } catch {
      throw new Error('平台返回了无效的响应地址');
    }
  };

  const fetchResponse = async (url, options, label) => {
    let response;
    try {
      response = await fetch(url, options);
    } catch {
      throw readerError(label + '网络请求失败', 'network');
    }
    const target = responseUrl(response, url);
    if (target.origin !== origin || isLoginPath(target.pathname)) {
      throw readerError('登录已失效，请重新登录', 'auth');
    }
    if (response.status === 401 || response.status === 403) {
      throw readerError('登录已失效，请重新登录', 'auth', response.status);
    }
    if (!response.ok) {
      throw readerError(
        label + '失败（HTTP ' + response.status + '）',
        'http',
        response.status
      );
    }
    return response;
  };

  const isLoginDocument = doc => Boolean(
    doc.querySelector('#login, form.login, input[name="username"]')
  );

  const output = [];
  const seenCourseIds = new Set();
  let offset = 0;
  for (let page = 0; page < 100; page++) {
    const body = [{
      index: 0,
      methodname: 'core_course_get_enrolled_courses_by_timeline_classification',
      args: {classification: 'all', limit: 0, offset}
    }];
    const coursesResponse = await fetchResponse(
      '/lib/ajax/service.php?sesskey=' + encodeURIComponent(sesskey),
      {
        method: 'POST',
        credentials: 'same-origin',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body)
      },
      '读取课程列表'
    );

    let courseResult;
    try {
      courseResult = await coursesResponse.json();
    } catch {
      throw readerError('课程列表返回的不是 JSON，可能尚未登录', 'auth');
    }
    const first = Array.isArray(courseResult) ? courseResult[0] : null;
    if (!first || first.error) {
      throw new Error('课程列表接口返回错误：' +
        (first?.exception?.message || '未知错误'));
    }
    if (!Array.isArray(first.data?.courses)) {
      throw new Error('课程列表格式与预期不同');
    }
    const courses = first.data.courses;
    const nextOffset = first.data.nextoffset;
    if (nextOffset != null &&
        (typeof nextOffset !== 'number' || !Number.isSafeInteger(nextOffset) || nextOffset < 0)) {
      throw new Error('课程列表分页偏移无效');
    }
    for (const course of courses) {
      if (!course || typeof course !== 'object') {
        throw new Error('课程列表包含无效条目');
      }
      const id = stableId(course.id);
      if (!id) {
        throw new Error('课程列表缺少稳定标识');
      }
      if (seenCourseIds.has(id)) {
        throw new Error('课程列表包含重复标识');
      }
      seenCourseIds.add(id);
      output.push({
        id,
        name: clean(course.fullname || course.shortname || id),
        assignments: []
      });
    }
    // Moodle 4.5 returns offset + processedcount, even with limit: 0.
    // An empty page completes traversal; its cursor may equal our offset.
    // Older limit: 0 responses omit the cursor or use zero to signal completion.
    if (courses.length === 0 || nextOffset == null || nextOffset === 0) {
      break;
    }
    if (nextOffset <= offset) {
      throw new Error('课程列表分页偏移没有前进');
    }
    if (page === 99) {
      throw new Error('课程列表分页超过 100 页，拒绝按完整列表处理');
    }
    offset = nextOffset;
  }

  for (const entry of output) {
    try {
      const result = await fetchResponse(
        '/mod/assign/index.php?id=' + encodeURIComponent(entry.id),
        {credentials: 'same-origin'},
        '读取课程作业索引'
      );
      const html = await result.text();
      const doc = new DOMParser().parseFromString(html, 'text/html');
      if (isLoginDocument(doc)) {
        throw new Error('登录已失效，请重新登录');
      }

      const tables = [...doc.querySelectorAll('table')];
      const table = tables.find(candidate =>
        candidate.querySelector('a[href*="/mod/assign/view.php"]')
      );
      if (!table) {
        const text = clean(doc.body?.textContent);
        if (!/没有作业|暂无作业|There are no assignments|No assignments/i.test(text)) {
          throw new Error('未找到作业表格或明确的空列表提示');
        }
        continue;
      }

      const headingRow = table.querySelector('thead tr') || table.querySelector('tr');
      const headings = headingRow
        ? [...headingRow.querySelectorAll('th,td')].map(cell => clean(cell.textContent))
        : [];
      const headingIndex = patterns =>
        headings.findIndex(heading => patterns.some(pattern => pattern.test(heading)));
      const dueIndex = headingIndex([/到期日期|截止日期|due date/i]);
      const statusIndex = headingIndex([/提交状态|状态|submission status|status/i]);
      const gradeIndex = headingIndex([/成绩|评分|grade/i]);
      const seenAssignmentIds = new Set();
      const detailFailures = [];
      let detailAuthError = '';

      for (const row of table.querySelectorAll('tr')) {
        const link = row.querySelector('a[href*="/mod/assign/view.php"]');
        if (!link) {
          continue;
        }
        const cells = [...row.querySelectorAll('td')];
        const linkIndex = cells.findIndex(cell => cell.contains(link));
        const resolvedStatusIndex = statusIndex >= 0 ? statusIndex : dueIndex + 1;
        const resolvedGradeIndex = gradeIndex >= 0 ? gradeIndex : resolvedStatusIndex + 1;
        if (linkIndex < 0 || dueIndex < 0 || !cells[dueIndex] ||
            !cells[resolvedStatusIndex] || !cells[resolvedGradeIndex]) {
          throw new Error('作业表格结构与预期不同');
        }

        const target = new URL(link.getAttribute('href'), result.url);
        if (target.origin !== origin || !/^\/mod\/assign\/view\.php$/i.test(target.pathname)) {
          throw new Error('作业链接地址异常');
        }
        const id = stableId(target.searchParams.get('id'));
        if (!id) {
          throw new Error('作业缺少稳定标识');
        }
        if (seenAssignmentIds.has(id)) {
          throw new Error('作业索引包含重复标识');
        }
        seenAssignmentIds.add(id);

        const status = clean(cells[resolvedStatusIndex].textContent);
        const assignment = {
          id,
          title: clean(link.textContent),
          url: target.href,
          due: clean(cells[dueIndex].textContent),
          status,
          submitted: /已提交|Submitted for grading|Submitted/i.test(status) &&
            !/未提交|Not submitted|No submission/i.test(status)
            ? true
            : /未提交|没有作业|尚未|No submission|Not submitted|Nothing submitted|Draft|草稿/i
              .test(status)
              ? false
              : null,
          grade: clean(cells[resolvedGradeIndex].textContent),
          content: '',
          comment: '',
          attachments: []
        };

        // Only the verified assignment link is followed. Unrecognised detail
        // fields remain empty; no additional private Moodle endpoint is assumed.
        try {
          const detailResponse = await fetchResponse(
            target.href,
            {credentials: 'same-origin'},
            '读取作业详情'
          );
          const detail = new DOMParser().parseFromString(
            await detailResponse.text(),
            'text/html'
          );
          if (isLoginDocument(detail)) {
            throw readerError('登录已失效，请重新登录', 'auth');
          }
          assignment.content = bodyText(
            detail.querySelector('#intro, .activity-description')
          );
          assignment.attachments = [
            ...detail.querySelectorAll(
              '#intro a[href*="pluginfile.php"], .activity-description a[href*="pluginfile.php"]'
            )
          ].map(anchor => clean(anchor.textContent)).filter(Boolean);
          for (const feedbackRow of detail.querySelectorAll(
            '.feedback table tr, .feedbacktable tr'
          )) {
            const feedbackCells = [...feedbackRow.querySelectorAll('th,td')];
            if (feedbackCells.length < 2) {
              continue;
            }
            const heading = clean(feedbackCells[0].textContent);
            if (/^(成绩|评分|Grade)$/i.test(heading)) {
              assignment.grade = clean(feedbackCells[1].textContent);
            }
            if (/反馈评语|反馈意见|Feedback comments/i.test(heading)) {
              assignment.comment = clean(feedbackCells[1].textContent);
            }
          }
        } catch (error) {
          assignment.detailUnavailable = true;
          detailFailures.push({
            id,
            reason: errorMessage(error)
          });
          if (error?.kind === 'auth') {
            detailAuthError = '详情读取时登录已失效，请重新登录';
          }
        }
        entry.assignments.push(assignment);
        if (detailAuthError) {
          break;
        }
      }
      if (detailFailures.length) {
        entry.detailsPartial = {
          count: detailFailures.length,
          assignmentIds: detailFailures.map(failure => failure.id),
          reasons: detailFailures,
          message: '部分作业详情暂不可用；已验证的作业索引仍保留'
        };
      }
      if (detailAuthError) {
        entry.error = detailAuthError;
      }
    } catch (error) {
      entry.assignments = [];
      entry.error = errorMessage(error);
    }
  }

  const partial = output.some(course =>
    Boolean(course.error || course.detailsPartial)
  );
  return partial ? {courses: output, partial: true} : {courses: output};
})().catch(error => ({
  error: error?.message || 'Moodle 读取失败，请检查网络或重新登录'
}))).then(data => {
  window.__njuHomeworkResult = data;
}).catch(() => {
  window.__njuHomeworkResult = {error: 'Moodle 读取失败'};
});

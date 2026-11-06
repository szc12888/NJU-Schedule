window.__njuHomeworkResult = null;
Promise.resolve(
(async () => {
  const origin = 'https://lms.nju.edu.cn';
  const maxPages = 100;

  if (location.origin !== origin) {
    return {error: '请先返回 LMS'};
  }

  const responseUrl = response => {
    try {
      return new URL(response.url || '/', origin);
    } catch {
      throw new Error('平台返回了无效的响应地址');
    }
  };

  const assertUsableResponse = (response, label) => {
    const url = responseUrl(response);
    if (url.origin !== origin || /\/login(?:\/|$)/i.test(url.pathname)) {
      throw new Error('登录已失效，请重新登录');
    }
    if (response.status === 401 || response.status === 403) {
      throw new Error('登录已失效，请重新登录');
    }
    if (!response.ok) {
      throw new Error(label + '失败（HTTP ' + response.status + '）');
    }
  };

  const getJson = async (url, label) => {
    let response;
    try {
      response = await fetch(url, {
        credentials: 'same-origin',
        headers: {'X-Requested-With': 'XMLHttpRequest'}
      });
    } catch {
      throw new Error(label + '网络请求失败');
    }
    assertUsableResponse(response, label);
    try {
      return await response.json();
    } catch {
      throw new Error(label + '返回的不是 JSON，可能尚未登录');
    }
  };

  const stableId = value => value == null ? '' : String(value).trim();

  const courseData = await getJson('/api/my-courses', '读取课程列表');
  if (!courseData || !Array.isArray(courseData.courses)) {
    throw new Error('课程列表格式与预期不同，请检查登录状态');
  }
  if (courseData.pages != null) {
    const coursePages = Number(courseData.pages);
    if (!Number.isInteger(coursePages) || coursePages < 0 || coursePages > 1 ||
        coursePages === 0 && courseData.courses.length > 0) {
      throw new Error('课程列表返回了多页或无效分页信息，当前没有已验证的翻页接口');
    }
  }

  const seenCourseIds = new Set();
  const courses = courseData.courses.map(course => {
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
    return {
      id,
      name: String(course.name || id),
      term: course.semester?.code || '未知',
      assignments: []
    };
  });

  const validatePage = (pageData, requestedPage, expectedPages) => {
    if (!pageData || !Array.isArray(pageData.homework_activities) ||
        !Number.isInteger(pageData.pages) || pageData.pages < 0 ||
        pageData.pages > maxPages) {
      throw new Error('作业列表格式或页数与预期不同');
    }
    if (expectedPages !== null && pageData.pages !== expectedPages) {
      throw new Error('分页总数在读取过程中发生变化');
    }
    if (pageData.pages === 0 && pageData.homework_activities.length !== 0) {
      throw new Error('分页信息与作业数据不一致');
    }
    if (pageData.pages > 0 && requestedPage > pageData.pages) {
      throw new Error('返回页码超过分页总数');
    }
    return pageData.pages;
  };

  // These paths and fields were checked in the authenticated Android page.
  const userId = stableId(window.globalData?.user?.id);
  const plain = html => {
    if (typeof html !== 'string' || !html.trim()) return '';
    const doc = new DOMParser().parseFromString(html, 'text/html');
    doc.querySelectorAll?.('script,style').forEach(node => node.remove());
    doc.querySelectorAll?.('li').forEach(node => {
      const parent = node.parentElement;
      if (parent?.tagName === 'OL') {
        const siblings = [...parent.children].filter(child => child.tagName === 'LI');
        const start = Number(parent.getAttribute('start') || 1);
        node.prepend(String((Number.isFinite(start) ? start : 1) + siblings.indexOf(node)) + '. ');
      } else node.prepend('• ');
    });
    doc.querySelectorAll?.('br').forEach(node => node.replaceWith('\n'));
    doc.querySelectorAll?.('p,div,li,h1,h2,h3,h4,tr').forEach(node => node.append('\n'));
    return (doc.body.textContent || '').replace(/[ \t]+/g, ' ').replace(/ *\n */g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  };
  const scalar = value => typeof value === 'string' || typeof value === 'number' && Number.isFinite(value) ? String(value).trim() : '';
  const attachmentNames = value => Array.isArray(value) ? value.map(upload => typeof upload?.name === 'string' ? upload.name.trim() : '').filter(Boolean) : [];
  let next = 0;
  const worker = async () => {
    while (next < courses.length) {
      const course = courses[next++];
      const seenAssignmentIds = new Set();
      try {
        let page = 1;
        let expectedPages = null;
        do {
          const pageData = await getJson(
            '/api/courses/' + encodeURIComponent(course.id) +
              '/homework-activities?page=' + page + '&page_size=50',
            '读取课程作业（第 ' + page + ' 页）'
          );
          expectedPages = validatePage(pageData, page, expectedPages);

          for (const task of pageData.homework_activities) {
            if (!task || typeof task !== 'object') {
              throw new Error('作业列表包含无效条目');
            }
            const id = stableId(task.id);
            if (!id) {
              throw new Error('作业缺少稳定标识');
            }
            if (seenAssignmentIds.has(id)) {
              throw new Error('分页返回了重复作业标识');
            }
            seenAssignmentIds.add(id);

            const assignment = {
              id, title: typeof task.title === 'string' ? task.title : '',
              due: task.deadline ?? null,
              submitted: typeof task.submitted === 'boolean' ? task.submitted : null,
              closed: typeof task.is_closed === 'boolean' ? task.is_closed : null,
              content: plain(task.data?.description || task.description || ''),
              grade: task.is_announce_score_time_passed === true ? scalar(task.score) : '',
              comment: '', attachments: attachmentNames(task.uploads), detailUnavailable: true
            };
            course.assignments.push(assignment);
            try {
              const detail = await getJson('/api/activities/' + encodeURIComponent(id), '读取作业详情');
              if (!detail || stableId(detail.id) !== id || !detail.data || typeof detail.data.description !== 'string' || !Array.isArray(detail.uploads)) throw new Error('作业详情结构发生变化');
              assignment.content = plain(detail.data.description);
              assignment.attachments = attachmentNames(detail.uploads);
              if (detail.can_show_score === false) assignment.grade = '';
              if (task.submitted === true) {
                if (!userId) throw new Error('未取得当前登录用户');
                const submission = await getJson('/api/course/activities/' + encodeURIComponent(id) + '/students/' + encodeURIComponent(userId) + '/submission', '读取作业反馈');
                if (!submission || stableId(submission.activity_id) !== id || !Object.prototype.hasOwnProperty.call(submission, 'score')) throw new Error('作业反馈结构发生变化');
                const visible = detail.can_show_score === true && submission.is_announce_score_time_passed === true;
                assignment.grade = visible ? scalar(submission.final_score) : '';
                const comments = visible ? [plain(submission.instructor_comment), plain(submission.submission_correct?.comment)].filter(Boolean) : [];
                assignment.comment = [...new Set(comments)].join('\n\n');
              }
              assignment.detailUnavailable = false;
            } catch {
              course.detailsPartial = true;
            }
          }

          page++;
        } while (expectedPages > 0 && page <= expectedPages);
      } catch (error) {
        course.assignments = [];
        course.error = error?.message || '课程作业读取失败';
      }
    }
  };

  await Promise.all(Array.from({length: Math.min(4, courses.length)}, worker));
  const partial = courses.some(course => Boolean(course.error));
  return partial ? {courses, partial: true} : {courses};
})().catch(error => ({
  error: error?.message || 'LMS 读取失败，请检查网络或重新登录'
}))).then(data => {
  window.__njuHomeworkResult = data;
}).catch(() => {
  window.__njuHomeworkResult = {error: 'LMS 读取失败'};
});

import {messagePreview,messageTitle} from './message-presentation';
import { getSessionMessages } from './api/session';
import { reviewPreview, looksLikeReview } from './review-result';

export async function withSessionPreviews(rows, request, owner) {
    const result = rows.map(row => ({ ...row, readableTitle: messageTitle(row.sessionTitle||''), preview: looksLikeReview(row.sessionContent)?reviewPreview(row.sessionContent):messagePreview(row.sessionContent||''), isReview: looksLikeReview(row.sessionContent) }));
    let next = 0;
    await Promise.all([0,1,2].map(async () => {
        while (next < result.length) {
            const row = result[next++];
            try {
                const page = await getSessionMessages(request, owner, row.id, { pageSize: 1 });
                const last = page.rows[0];
                if (last) {
                    row.preview = last.role === 'assistant'&&looksLikeReview(last.content)?reviewPreview(last.content):messagePreview(last.content,last.role==='assistant'?'assistant':'user');
                    row.isReview = last.role === 'assistant' && looksLikeReview(last.content);
                }
            } catch (_) { /* Keep the session accessible when its preview cannot be read. */ }
        }
    }));
    return result;
}

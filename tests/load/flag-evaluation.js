import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 50,
    duration: '30s',
    thresholds: {
        http_req_duration: ['p(95)<50'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const url = 'http://localhost/api/v1/flags/evaluate?key=dark-mode&userId=test-user&environment=production';
    const res = http.get(url);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'is served from cache': (r) => r.headers['X-Cache-Status'] === 'HIT',
    });

    sleep(0.1);
}

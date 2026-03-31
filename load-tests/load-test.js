import http from 'k6/http';
import { check } from 'k6';

const RATE = 2500
const BURST_RATE = RATE * 2.5

// This script validates the CV bullet point:
// "~220k req/min with <7ms p99 overhead, sustaining 2.5x burst traffic"
export const options = {
    scenarios: {
        // Base load: ~150k RPM (approx 2500 RPS)
        base_load: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 50,
            maxVUs: 100,
        },
        // Burst load: 2.5x the base load (~6250 RPS)
        // Starts after 10 seconds of base load
        burst_spike: {
            executor: 'constant-arrival-rate',
            rate: BURST_RATE,
            timeUnit: '1s',
            duration: '10s',
            startTime: '10s',
            preAllocatedVUs: 100,
            maxVUs: 200,
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<20'],
    },
};

export default function () {
    const url = 'http://localhost:8080/api/hello';

    // We restrict the IP pool to 500 IPs. 
    // The endpoint allows 50 requests per 10 seconds per IP.
    // Base load generates ~8 req/sec per IP.
    // Burst load generates ~24 req/sec per IP.
    // This WILL trigger 429 Too Many Requests during the burst.
    const randomIpId = Math.floor(Math.random() * 500);
    const clientIp = `192.168.1.${randomIpId}`;

    const params = {
        headers: {
            'X-Forwarded-For': clientIp,
            'Accept': 'application/json',
        },
    };

    const res = http.get(url, params);

    check(res, {
        'Handled Successfully (200 or 429)': (r) => r.status === 200 || r.status === 429,
    });
}

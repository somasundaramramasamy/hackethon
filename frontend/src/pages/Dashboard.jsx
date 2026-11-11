import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  FileText,
  CircleCheck,
  CircleX,
  TriangleAlert,
  ArrowUp,
  ArrowRight,
  ChartColumn,
  Shield,
  Globe,
  Target,
  Sparkles,
  Layers,
  TrendingUp,
  ChevronDown,
} from 'lucide-react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  LabelList,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Cell,
} from 'recharts'
import { getDashboard } from '../api/client'
import { Card, Loading, ErrorState } from '../components/ui'
import RunsTable from '../components/RunsTable'

const LANG_COLORS = { EN: '#3b6cf6', TA: '#8b5cf6', HI: '#f9a8b8' }

export default function Dashboard() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getDashboard().then(setData).catch((e) => setError(e.message))
  }, [])

  if (error) return <ErrorState message={error} />
  if (!data) return <Loading />

  const { stats } = data
  const pct = (n) => ((n / stats.totalTests) * 100).toFixed(1)

  return (
    <div className="dashboard">
      <div className="stat-grid">
        <StatCard tone="blue" icon={FileText} label="Total Tests" value={stats.totalTests}>
          <span className="up">
            <ArrowUp size={14} /> +{stats.totalChangePct}%
          </span>{' '}
          from last run
        </StatCard>
        <StatCard tone="green" icon={CircleCheck} label="Passed" value={stats.passed}>
          {pct(stats.passed)}% pass rate
        </StatCard>
        <StatCard tone="red" icon={CircleX} label="Failed" value={stats.failed}>
          {pct(stats.failed)}% failure rate
        </StatCard>
        <StatCard tone="amber" icon={TriangleAlert} label="Needs Review" value={stats.needsReview}>
          {pct(stats.needsReview)}% of total tests
        </StatCard>
      </div>

      <div className="row-2">
        <Card
          title="Recent Test Runs"
          icon={ChartColumn}
          action={
            <Link to="/runs" className="link">
              View All Runs <ArrowRight size={16} />
            </Link>
          }
        >
          <RunsTable runs={data.recentRuns} />
        </Card>
        <TrendCard trend={data.trend} />
      </div>

      <div className="row-3">
        <CoverageCard coverage={data.coverage} />
        <Card title="Failure Reasons (Top 3)" icon={Target}>
          <div className="bars">
            {data.failureReasons.map((r) => (
              <div key={r.label} className="bar-row">
                <div className="bar-label">{r.label}</div>
                <div className="bar-line">
                  <div className="bar-track">
                    <div className="bar-fill" style={{ width: `${r.pct}%` }} />
                  </div>
                  <span className="bar-pct">{r.pct}%</span>
                </div>
              </div>
            ))}
          </div>
        </Card>
        <Card title="Key Insights" icon={Sparkles}>
          <ul className="insights">
            {data.insights.map((ins) => (
              <li key={ins.icon} className={`insight insight-${ins.tone}`}>
                <InsightIcon name={ins.icon} />
                <span>
                  {ins.parts.map((p, i) =>
                    p.b ? <b key={i}>{p.t}</b> : <span key={i} className={p.up ? 'up' : undefined}>{p.t}</span>,
                  )}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  )
}

function StatCard({ tone, icon: Icon, label, value, children }) {
  return (
    <div className={`stat stat-${tone}`}>
      <div className="stat-icon">
        <Icon size={tone === 'blue' ? 30 : 26} />
      </div>
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value.toLocaleString()}</div>
        <div className="stat-sub">{children}</div>
      </div>
    </div>
  )
}

function TrendCard({ trend }) {
  const [count, setCount] = useState(7)
  const points = useMemo(() => trend.slice(-count), [trend, count])
  const last = points.at(-1)

  return (
    <Card
      title="Safety Score Trend"
      icon={Shield}
      className="trend-card"
      action={
        <label className="select-wrap">
          <select value={count} onChange={(e) => setCount(Number(e.target.value))} aria-label="Range">
            <option value={5}>Last 5 Runs</option>
            <option value={7}>Last 7 Runs</option>
            <option value={10}>Last 10 Runs</option>
          </select>
          <ChevronDown size={16} />
        </label>
      }
    >
      <div className="trend-chart">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={points} margin={{ top: 28, right: 28, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#7c5cf6" stopOpacity={0.28} />
                <stop offset="100%" stopColor="#7c5cf6" stopOpacity={0.03} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} stroke="#eef0f6" />
            <XAxis dataKey="run" padding={{ left: 20, right: 4 }} tickLine={false} axisLine={{ stroke: '#d9dce6' }} tick={{ fontSize: 11, fill: '#3c4257' }} tickFormatter={(v) => (points.length > 7 ? v.slice(3) : v)} interval={0} />
            <YAxis
              domain={[70, 100]}
              ticks={[70, 75, 80, 85, 90, 95, 100]}
              tickFormatter={(v) => `${v}%`}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 12, fill: '#3c4257' }}
              width={44}
            />
            <Tooltip formatter={(v) => [`${v}%`, 'Safety score']} />
            <Area
              type="linear"
              dataKey="score"
              stroke="#7c5cf6"
              strokeWidth={2.5}
              fill="url(#trendFill)"
              dot={{ r: 4, stroke: '#7c5cf6', strokeWidth: 2, fill: '#fff' }}
              activeDot={{ r: 6 }}
              isAnimationActive={false}
            >
              <LabelList
                dataKey="score"
                content={({ x, y, value, index }) =>
                  index === points.length - 1 ? (
                    <g>
                      <rect x={x - 26} y={y - 34} width={52} height={24} rx={6} fill="#7c5cf6" />
                      <text x={x} y={y - 17} textAnchor="middle" fill="#fff" fontSize={13} fontWeight={600}>
                        {value}%
                      </text>
                    </g>
                  ) : (
                    <text x={x} y={y - 12} textAnchor="middle" fill="#1f2433" fontSize={12} fontWeight={500}>
                      {value}%
                    </text>
                  )
                }
              />
            </Area>
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <span className="sr-only">Latest safety score {last?.score}%</span>
    </Card>
  )
}

function CoverageCard({ coverage }) {
  const total = coverage.reduce((s, c) => s + c.count, 0)
  return (
    <Card title="Test Coverage by Language" icon={Globe}>
      <div className="coverage">
        <div className="donut">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={coverage}
                dataKey="count"
                nameKey="name"
                innerRadius="64%"
                outerRadius="100%"
                startAngle={90}
                endAngle={-270}
                stroke="none"
                isAnimationActive={false}
              >
                {coverage.map((c) => (
                  <Cell key={c.code} fill={LANG_COLORS[c.code]} />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
          <div className="donut-center">
            <b>{total.toLocaleString()}</b>
            <span>Tests</span>
          </div>
        </div>
        <ul className="legend">
          {coverage.map((c) => (
            <li key={c.code}>
              <span className="dot" style={{ background: LANG_COLORS[c.code] }} />
              <span className="legend-name">{c.name}</span>
              <span className="legend-val">
                {c.count} ({((c.count / total) * 100).toFixed(1)}%)
              </span>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  )
}

function InsightIcon({ name }) {
  const props = { size: 20, className: 'insight-icon' }
  if (name === 'check') return <CircleCheck {...props} />
  if (name === 'alert') return <TriangleAlert {...props} />
  if (name === 'layers') return <Layers {...props} />
  return <TrendingUp {...props} />
}

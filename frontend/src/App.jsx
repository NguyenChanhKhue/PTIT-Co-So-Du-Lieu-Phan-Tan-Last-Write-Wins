import { useEffect, useState } from 'react'
import './App.css'

const NODE_CONFIG = [
  { id: '1', name: 'Node 1', label: 'Normal Clock', port: 8081, tone: 'node-normal' },
  { id: '2', name: 'Node 2', label: 'Fast Clock', port: 8082, tone: 'node-fast' },
  { id: '3', name: 'Node 3', label: 'Slow Clock', port: 8083, tone: 'node-slow' },
]

const initialForm = {
  cid: 'DEMO1',
  address: 'Initial address from fast node',
  phone: '2222',
}

const initialComparison = NODE_CONFIG.map((node) => ({
  nodeId: node.id,
  status: 'idle',
  customer: null,
  error: null,
}))

const normalizeText = (value) => value ?? ''

const didWriteApply = (result, payload, targetNodeId) =>
  result?.cid === payload.cid &&
  normalizeText(result?.address) === normalizeText(payload.address) &&
  normalizeText(result?.phone) === normalizeText(payload.phone) &&
  result?.lastUpdatedByNode === targetNodeId

function App() {
  const [nodes, setNodes] = useState(
    NODE_CONFIG.map((node) => ({ ...node, status: 'loading', info: null, error: null })),
  )
  const [selectedNode, setSelectedNode] = useState('2')
  const [form, setForm] = useState(initialForm)
  const [comparison, setComparison] = useState(initialComparison)
  const [activityLog, setActivityLog] = useState([
    {
      id: crypto.randomUUID(),
      type: 'system',
      title: 'Dashboard ready',
      detail: 'Use the controls below to write into one node and watch replication across the cluster.',
      timestamp: new Date().toLocaleTimeString(),
    },
  ])
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

  const appendLog = (type, title, detail) => {
    setActivityLog((current) => [
      {
        id: crypto.randomUUID(),
        type,
        title,
        detail,
        timestamp: new Date().toLocaleTimeString(),
      },
      ...current,
    ])
  }

  const fetchNodeInfo = async (node) => {
    const response = await fetch(`http://localhost:${node.port}/api/node/info`)
    if (!response.ok) {
      throw new Error(`Node ${node.id} returned ${response.status}`)
    }
    return response.json()
  }

  const loadNodes = async () => {
    setIsRefreshing(true)

    const settled = await Promise.allSettled(
      NODE_CONFIG.map(async (node) => ({
        node,
        info: await fetchNodeInfo(node),
      })),
    )

    setNodes(
      settled.map((result, index) => {
        const node = NODE_CONFIG[index]
        if (result.status === 'fulfilled') {
          return { ...node, status: 'online', info: result.value.info, error: null }
        }
        return {
          ...node,
          status: 'offline',
          info: null,
          error: result.reason instanceof Error ? result.reason.message : 'Unable to connect',
        }
      }),
    )

    setIsRefreshing(false)
  }

  const fetchCustomerForNode = async (node, cid) => {
    const response = await fetch(`http://localhost:${node.port}/api/customer/${encodeURIComponent(cid)}`)
    if (response.status === 404) {
      return null
    }
    if (!response.ok) {
      throw new Error(`Node ${node.id} returned ${response.status}`)
    }
    return response.json()
  }

  const refreshCustomerView = async (cid) => {
    if (!cid.trim()) {
      setComparison(initialComparison)
      return
    }

    const settled = await Promise.allSettled(
      NODE_CONFIG.map(async (node) => ({
        nodeId: node.id,
        customer: await fetchCustomerForNode(node, cid),
      })),
    )

    setComparison(
      settled.map((result, index) => {
        if (result.status === 'fulfilled') {
          return {
            nodeId: NODE_CONFIG[index].id,
            status: result.value.customer ? 'found' : 'missing',
            customer: result.value.customer,
            error: null,
          }
        }
        return {
          nodeId: NODE_CONFIG[index].id,
          status: 'error',
          customer: null,
          error: result.reason instanceof Error ? result.reason.message : 'Unknown error',
        }
      }),
    )
  }

  useEffect(() => {
    void loadNodes()
    void refreshCustomerView(initialForm.cid)

    const timer = window.setInterval(() => {
      void loadNodes()
    }, 8000)

    return () => window.clearInterval(timer)
  }, [])

  const handleFormChange = (event) => {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  const writeCustomer = async (targetNodeId, payload, title) => {
    const node = NODE_CONFIG.find((item) => item.id === targetNodeId)
    if (!node) {
      return
    }

    const response = await fetch(`http://localhost:${node.port}/api/customer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })

    if (!response.ok) {
      throw new Error(`Write to Node ${targetNodeId} failed with ${response.status}`)
    }

    const result = await response.json()
    const applied = didWriteApply(result, payload, targetNodeId)

    appendLog(
      applied ? 'write' : 'warning',
      title,
      applied
        ? `${node.name} applied CID ${result.cid} at ${result.updateTimestampReadable}. This value should replicate as the current winner.`
        : `${node.name} received the request for CID ${payload.cid}, but LWW kept ${result.lastUpdatedByNode || 'another node'} as winner at ${result.updateTimestampReadable}.`,
    )

    return { result, applied }
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setIsSubmitting(true)

    try {
      await writeCustomer(selectedNode, form, `Manual write on Node ${selectedNode}`)
      await Promise.all([loadNodes(), refreshCustomerView(form.cid)])
    } catch (error) {
      appendLog(
        'error',
        'Write failed',
        error instanceof Error ? error.message : 'Unexpected error while writing customer data.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const clearCluster = async () => {
    setIsSubmitting(true)
    try {
      await Promise.all(
        NODE_CONFIG.map(async (node) => {
          await fetch(`http://localhost:${node.port}/api/customers`, { method: 'DELETE' })
        }),
      )
      appendLog('system', 'Cluster reset', 'All three node databases were cleared for a fresh demo run.')
      await Promise.all([loadNodes(), refreshCustomerView(form.cid)])
    } catch (error) {
      appendLog(
        'error',
        'Reset failed',
        error instanceof Error ? error.message : 'Unable to clear one or more node databases.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const runFastClockScenario = async () => {
    setIsSubmitting(true)
    const cid = 'CLOCK_FAST'
    try {
      setForm({
        cid,
        address: 'Fast node writes first',
        phone: '2222',
      })
      await Promise.all(
        NODE_CONFIG.map(async (node) => {
          await fetch(`http://localhost:${node.port}/api/customers`, { method: 'DELETE' })
        }),
      )

      const firstWrite = await writeCustomer(
        '2',
        { cid, address: 'Fast node writes first', phone: '2222' },
        'Scenario step 1: Node 2 writes first',
      )

      await new Promise((resolve) => window.setTimeout(resolve, 1000))

      const secondWrite = await writeCustomer(
        '1',
        { cid, address: 'Normal node writes later', phone: '1111' },
        'Scenario step 2: Node 1 writes later',
      )

      await Promise.all([loadNodes(), refreshCustomerView(cid)])
      appendLog(
        secondWrite?.applied ? 'error' : 'warning',
        'Expected clock skew conflict',
        secondWrite?.applied
          ? 'Node 1 unexpectedly won in this run. That means the fast-clock conflict was not reproduced.'
          : `Node 2 kept the winning value from ${firstWrite?.result?.updateTimestampReadable || 'its earlier write'}, showing that a later real-time write can still lose under LWW when another node clock is fast.`,
      )
    } catch (error) {
      appendLog(
        'error',
        'Scenario failed',
        error instanceof Error ? error.message : 'Unable to run the fast-clock scenario.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const runSlowClockScenario = async () => {
    setIsSubmitting(true)
    const cid = 'CLOCK_SLOW'
    try {
      setForm({
        cid,
        address: 'Normal node writes first',
        phone: '1111',
      })
      await Promise.all(
        NODE_CONFIG.map(async (node) => {
          await fetch(`http://localhost:${node.port}/api/customers`, { method: 'DELETE' })
        }),
      )

      const firstWrite = await writeCustomer(
        '1',
        { cid, address: 'Normal node writes first', phone: '1111' },
        'Scenario step 1: Node 1 writes first',
      )

      await new Promise((resolve) => window.setTimeout(resolve, 1000))

      const secondWrite = await writeCustomer(
        '3',
        { cid, address: 'Slow node writes later', phone: '3333' },
        'Scenario step 2: Node 3 writes later',
      )

      await Promise.all([loadNodes(), refreshCustomerView(cid)])
      appendLog(
        secondWrite?.applied ? 'error' : 'warning',
        'Expected slow-clock rejection',
        secondWrite?.applied
          ? 'Node 3 unexpectedly won in this run. That means the slow-clock rejection was not reproduced.'
          : `Node 1 kept the winning value from ${firstWrite?.result?.updateTimestampReadable || 'its earlier write'}, showing that a slow node can lose even when it writes later in real time.`,
      )
    } catch (error) {
      appendLog(
        'error',
        'Scenario failed',
        error instanceof Error ? error.message : 'Unable to run the slow-clock scenario.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const selectedNodeMeta = NODE_CONFIG.find((node) => node.id === selectedNode)

  return (
    <div className="app-shell">
      <header className="hero-panel">
        <div className="hero-copy">
          <p className="eyebrow">Distributed Database Demo</p>
          <h1>Last Write Wins under real clock skew</h1>
          <p className="hero-text">
            This dashboard lets you write into any node, observe replication across three SQLite
            databases, and demonstrate why LWW can pick the wrong winner when clocks diverge.
          </p>
        </div>
        <div className="hero-metrics">
          <div className="metric-card">
            <span>Cluster state</span>
            <strong>{nodes.filter((node) => node.status === 'online').length}/3 online</strong>
          </div>
          <div className="metric-card">
            <span>Selected write target</span>
            <strong>{selectedNodeMeta?.name}</strong>
          </div>
          <div className="metric-card">
            <span>Observed CID</span>
            <strong>{form.cid || 'None'}</strong>
          </div>
        </div>
      </header>

      <section className="cluster-grid">
        {nodes.map((node) => (
          <article className={`node-card ${node.tone}`} key={node.id}>
            <div className="node-card__header">
              <div>
                <p className="node-name">{node.name}</p>
                <h2>{node.label}</h2>
              </div>
              <span className={`status-pill status-${node.status}`}>{node.status}</span>
            </div>

            {node.info ? (
              <div className="node-stats">
                <div>
                  <span>Port</span>
                  <strong>{node.port}</strong>
                </div>
                <div>
                  <span>Clock skew</span>
                  <strong>{node.info.clockSkewSeconds}s</strong>
                </div>
                <div>
                  <span>Readable warning</span>
                  <strong>{node.info.dangerWarning}</strong>
                </div>
                <div>
                  <span>Customers</span>
                  <strong>{node.info.customerCount}</strong>
                </div>
                <div>
                  <span>Skewed timestamp</span>
                  <strong>{node.info.currentSkewedTimestamp}</strong>
                </div>
                <div>
                  <span>Real timestamp</span>
                  <strong>{node.info.currentRealTimestamp}</strong>
                </div>
              </div>
            ) : (
              <p className="node-error">{node.error || 'Waiting for backend response...'}</p>
            )}
          </article>
        ))}
      </section>

      <section className="workspace">
        <div className="control-panel">
          <div className="panel-heading">
            <p className="eyebrow">Write Controls</p>
            <h2>Send a customer update into one node</h2>
          </div>

          <form className="customer-form" onSubmit={handleSubmit}>
            <label>
              Target node
              <select value={selectedNode} onChange={(event) => setSelectedNode(event.target.value)}>
                {NODE_CONFIG.map((node) => (
                  <option key={node.id} value={node.id}>
                    {node.name} - {node.label}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Customer ID
              <input name="cid" value={form.cid} onChange={handleFormChange} placeholder="CID" />
            </label>

            <label>
              Address
              <input
                name="address"
                value={form.address}
                onChange={handleFormChange}
                placeholder="Customer address"
              />
            </label>

            <label>
              Phone
              <input
                name="phone"
                value={form.phone}
                onChange={handleFormChange}
                placeholder="Phone number"
              />
            </label>

            <div className="button-row">
              <button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Sending...' : 'Write Customer'}
              </button>
              <button
                type="button"
                className="ghost-button"
                disabled={isSubmitting}
                onClick={() => void refreshCustomerView(form.cid)}
              >
                Refresh Comparison
              </button>
              <button
                type="button"
                className="ghost-button"
                disabled={isSubmitting}
                onClick={() => void loadNodes()}
              >
                {isRefreshing ? 'Refreshing...' : 'Refresh Nodes'}
              </button>
            </div>
          </form>

          <div className="scenario-block">
            <div>
              <p className="eyebrow">Scenario Shortcuts</p>
              <h3>Run the clock skew demos in one click</h3>
            </div>

            <div className="scenario-actions">
              <button type="button" disabled={isSubmitting} onClick={runFastClockScenario}>
                Demo Fast Clock Error
              </button>
              <button type="button" disabled={isSubmitting} onClick={runSlowClockScenario}>
                Demo Slow Clock Error
              </button>
              <button type="button" className="ghost-button" disabled={isSubmitting} onClick={clearCluster}>
                Clear Cluster
              </button>
            </div>

            <p className="scenario-note">
              Fast clock scenario: Node 2 writes first, Node 1 writes later, but Node 2 may still
              win. Slow clock scenario: Node 1 writes first, Node 3 writes later, but Node 1 may
              still win because Node 3 carries an older timestamp.
            </p>
          </div>
        </div>

        <div className="observation-panel">
          <div className="panel-heading">
            <p className="eyebrow">Replication View</p>
            <h2>Compare one customer across all three nodes</h2>
          </div>

          <div className="comparison-grid">
            {comparison.map((entry) => {
              const nodeMeta = NODE_CONFIG.find((node) => node.id === entry.nodeId)
              return (
                <article key={entry.nodeId} className={`comparison-card ${nodeMeta?.tone || ''}`}>
                  <div className="comparison-card__top">
                    <span>{nodeMeta?.name}</span>
                    <strong>{entry.status}</strong>
                  </div>
                  {entry.customer ? (
                    <dl>
                      <div>
                        <dt>Address</dt>
                        <dd>{entry.customer.address || '-'}</dd>
                      </div>
                      <div>
                        <dt>Phone</dt>
                        <dd>{entry.customer.phone || '-'}</dd>
                      </div>
                      <div>
                        <dt>Timestamp</dt>
                        <dd>{entry.customer.updateTimestamp}</dd>
                      </div>
                      <div>
                        <dt>Readable time</dt>
                        <dd>{entry.customer.updateTimestampReadable || '-'}</dd>
                      </div>
                      <div>
                        <dt>Winner node</dt>
                        <dd>{entry.customer.lastUpdatedByNode || '-'}</dd>
                      </div>
                      <div>
                        <dt>Version</dt>
                        <dd>{entry.customer.version}</dd>
                      </div>
                    </dl>
                  ) : (
                    <p className="node-error">
                      {entry.status === 'missing'
                        ? 'Customer not found on this node yet.'
                        : entry.error || 'No customer selected.'}
                    </p>
                  )}
                </article>
              )
            })}
          </div>

          <div className="timeline-panel">
            <div className="panel-heading timeline-heading">
              <div>
                <p className="eyebrow">Live Activity</p>
                <h2>What just happened in the cluster</h2>
              </div>
            </div>

            <div className="timeline-list">
              {activityLog.map((item) => (
                <article className={`timeline-item timeline-${item.type}`} key={item.id}>
                  <div className="timeline-item__meta">
                    <strong>{item.title}</strong>
                    <span>{item.timestamp}</span>
                  </div>
                  <p>{item.detail}</p>
                </article>
              ))}
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}

export default App

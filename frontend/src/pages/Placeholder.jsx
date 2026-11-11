import { Link } from 'react-router-dom'
import { Wrench } from 'lucide-react'

export default function Placeholder({ title }) {
  return (
    <div className="card placeholder">
      <Wrench size={36} />
      <h2>{title}</h2>
      <p>This screen is planned but not built yet.</p>
      <Link to="/runs/TR-2026-0142/review" className="link">
        Open the sample Test Run Review
      </Link>
    </div>
  )
}
